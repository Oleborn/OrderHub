package oleborn.order_service.order.cache;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.OrderResponseDto;
import oleborn.order_service.order.domain.entity.Order;
import oleborn.order_service.order.exception.NotFoundOrderException;
import oleborn.order_service.order.repository.OrderRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class OrderCacheService {

    // Префикс для ключей в Redis, чтобы легко идентифицировать записи этого сервиса
    private static final String CACHE_KEY_PREFIX = "order:";

    // Базовое время жизни записи в кэше (5 минут)
    private static final Duration BASE_TTL = Duration.ofMinutes(5);

    // Максимальная случайная добавка к TTL (60 секунд) – для защиты от Cache Stampede
    private static final long JITTER_SECONDS = 60;

    // Шаблон Redis для выполнения операций с ключ-значение
    private final RedisTemplate<String, OrderResponseDto> orderRedisTemplate;

    // Репозиторий для загрузки заказов из БД, если их нет в кэше
    private final OrderRepository orderRepository;

    // Счётчик попаданий в кэш (используется в метриках)
    private final Counter cacheHitCounter;

    // Счётчик промахов в кэш (используется в метриках)
    private final Counter cacheMissCounter;

    // Конструктор – внедряем зависимости через Spring
    public OrderCacheService(
            RedisTemplate<String, OrderResponseDto> orderRedisTemplate,
            OrderRepository orderRepository,
            MeterRegistry meterRegistry // Реестр метрик, чтобы зарегистрировать счётчики
    ) {
        this.orderRedisTemplate = orderRedisTemplate;
        this.orderRepository = orderRepository;

        // Создаём счётчик для попаданий и регистрируем его в Micrometer
        // Имя метрики: order.cache.redis.hit
        this.cacheHitCounter = Counter.builder("order.cache.redis.hit")
                .description("Redis cache hits")
                .register(meterRegistry);

        // Счётчик для промахов
        this.cacheMissCounter = Counter.builder("order.cache.redis.miss")
                .description("Redis cache misses")
                .register(meterRegistry);
    }

    // Основной публичный метод получения заказа по ID с использованием кэша
    public OrderResponseDto getOrder(Long id) {

        // 1. Пытаемся получить заказ из Redis через защищённый метод (с CircuitBreaker)
        OrderResponseDto cachedOrder = getFromRedis(id);

        // Если заказ найден в кэше – инкрементируем счётчик попаданий и возвращаем
        if (cachedOrder != null) {
            cacheHitCounter.increment();

            log.debug(
                    "Redis cache hit for order {}",
                    id
            );

            return cachedOrder;
        }

        // Если в кэше нет – инкрементируем промахи
        cacheMissCounter.increment();

        log.debug(
                "Redis cache miss for order {}",
                id
        );

        // 2. Загружаем заказ из БД (с JOIN на позиции заказа)
        Order order = orderRepository
                .findWithItemsById(id)
                .orElseThrow(() ->
                        new NotFoundOrderException(
                                "Order not found: " + id
                        )
                );

        // 3. Сохраняем загруженный заказ в Redis (с TTL + jitter)

        OrderResponseDto dto = OrderResponseDto.from(order);
        saveToRedis(dto);

        // 4. Возвращаем заказ клиенту
        return dto;
    }

    // Метод для получения заказа из Redis, защищённый CircuitBreaker
    // Если Redis недоступен или возникают исключения – сработает fallback
    @CircuitBreaker(
            name = "redisCache", // Имя экземпляра CircuitBreaker (настраивается в application.yaml)
            fallbackMethod = "getFromRedisFallback" // Метод, который будет вызван при ошибке
    )
    protected OrderResponseDto getFromRedis(Long id) {
        // Выполняем Redis GET по ключу, сформированному из ID
        // Возвращаем объект Order (если есть) или null
        return orderRedisTemplate
                .opsForValue()
                .get(buildKey(id));
    }

    // Fallback-метод для getFromRedis – вызывается, если Redis не отвечает или ошибка
    protected OrderResponseDto getFromRedisFallback(
            Long id,
            Throwable throwable // Исключение, которое вызвало fallback
    ) {
        log.warn(
                "Redis is unavailable for order {}. " +
                "Falling back to database.",
                id,
                throwable
        );

        // Возвращаем null, чтобы метод getOrder пошёл в БД
        return null;
    }

    // Метод для сохранения заказа в Redis, также защищён CircuitBreaker
    @CircuitBreaker(
            name = "redisCache",
            fallbackMethod = "saveToRedisFallback"
    )
    protected void saveToRedis(OrderResponseDto dto) {
        // Генерируем случайную добавку к TTL от 0 до 60 секунд (Jitter)
        long jitterSeconds =
                ThreadLocalRandom.current()
                        .nextLong(JITTER_SECONDS);

        // Итоговое время жизни = базовое 5 мин + случайная добавка
        Duration ttl =
                BASE_TTL.plusSeconds(jitterSeconds);

        // Сохраняем заказ в Redis по ключу с TTL
        orderRedisTemplate.opsForValue().set(
                buildKey(dto.id()),
                dto,
                ttl
        );

        log.debug(
                "Order {} saved to Redis with TTL {} seconds",
                dto.id(),
                ttl.getSeconds()
        );
    }

    // Fallback для saveToRedis – если Redis недоступен, просто логируем ошибку
    protected void saveToRedisFallback(
            Order order,
            Throwable throwable
    ) {
        log.warn(
                "Failed to save order {} to Redis",
                order.getId(),
                throwable
        );
        // Ничего не делаем – заказ уже сохранён в БД, кэш не обновлён, но это не критично
    }

    // Публичный метод для принудительной инвалидации (удаления) заказа из кэша
    // Вызывается при обновлении заказа (например, после оплаты или отмены)
    @CircuitBreaker(
            name = "redisCache",
            fallbackMethod = "evictFallback"
    )
    public void evict(Long id) {
        // Удаляем запись из Redis по ключу
        orderRedisTemplate.delete(buildKey(id));

        log.debug(
                "Order {} evicted from Redis",
                id
        );
    }

    // Fallback для evict – если Redis недоступен, логируем предупреждение
    protected void evictFallback(
            Long id,
            Throwable throwable
    ) {
        log.warn(
                "Failed to evict order {} from Redis",
                id,
                throwable
        );
        // Если не удалось удалить, то кэш может содержать устаревшие данные до истечения TTL
        // Это допустимый компромисс – в худшем случае данные будут неактуальны не более 5-6 минут
    }

    // Вспомогательный метод для формирования ключа в Redis
    private String buildKey(Long id) {
        return CACHE_KEY_PREFIX + id; // например, "order:123"
    }
}