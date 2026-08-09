package oleborn.order_service.order.cache;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.OrderResponseDto;
import oleborn.order_service.order.exception.NotFoundOrderException;
import oleborn.order_service.order.repository.OrderRepository;
import oleborn.order_service.order.service.OrderService;
import org.springframework.stereotype.Service;

/**
 * Сервис локального кэширования заказов на основе Caffeine.
 * <p>
 * Этот сервис обеспечивает быстрый доступ к заказам без обращения к базе данных,
 * используя встроенный in-memory кэш. Он решает ключевую проблему повторных SELECT-запросов,
 * которые создают избыточную нагрузку на PostgreSQL.
 * <p>
 * <b>Основные возможности:</b>
 * <ul>
 *   <li>Автоматическая загрузка данных при промахе (cache‑load)</li>
 *   <li>Атомарная операция «получить или загрузить» — даже при конкурентных запросах одного ключа
 *       БД будет вызвана только один раз (single‑flight / coalescing)</li>
 *   <li>Ограничение размера кэша (конфигурируется в {@link CaffeineConfig})</li>
 *   <li>Время жизни записи (TTL) — автоматическое удаление устаревших данных</li>
 *   <li>Полная статистика попаданий, промахов, вытеснений и времени загрузки</li>
 *   <li>Возможность ручной инвалидации при обновлении заказа</li>
 * </ul>
 * <p>
 * <b>Как это работает:</b>
 * <ol>
 *   <li>При вызове {@link #getOrder(Long)} метод сначала проверяет наличие записи в кэше
 *       по ключу (id заказа).</li>
 *   <li>Если запись присутствует — она возвращается мгновенно (попадание / hit).</li>
 *   <li>Если записи нет — Caffeine вызывает переданную функцию загрузки, которая выполняет
 *       запрос к {@link OrderRepository#findWithItemsById(Long)}.</li>
 *   <li>Загруженный объект сохраняется в кэш, и в дальнейшем все запросы по тому же id
 *       будут обслуживаться из памяти.</li>
 *   <li>Запись автоматически удаляется по истечении TTL (задаётся в конфигурации) или
 *       при превышении максимального размера (вытеснение по алгоритму TinyLFU).</li>
 *   <li>При обновлении заказа (изменение статуса) вызывается {@link #evict(Long)},
 *       чтобы удалить устаревшую копию и принудительно перезагрузить свежие данные
 *       при следующем запросе.</li>
 * </ol>
 * <p>
 * <b>Метрики:</b>
 * <p>
 * После регистрации через {@link CaffeineCacheMetrics#monitor} в Micrometer становятся доступны:
 * <ul>
 *   <li>{@code cache.order.caffeine.hitCount} — общее количество попаданий</li>
 *   <li>{@code cache.order.caffeine.missCount} — общее количество промахов</li>
 *   <li>{@code cache.order.caffeine.hitRate} — доля попаданий (0..1)</li>
 *   <li>{@code cache.order.caffeine.missRate} — доля промахов</li>
 *   <li>{@code cache.order.caffeine.loadSuccessCount} — успешных загрузок из БД</li>
 *   <li>{@code cache.order.caffeine.loadFailureCount} — ошибок при загрузке</li>
 *   <li>{@code cache.order.caffeine.evictionCount} — количество вытеснений</li>
 *   <li>{@code cache.order.caffeine.size} — текущее количество записей в кэше</li>
 * </ul>
 * Все метрики помечены тегом {@code cache_type="caffeine"}, что позволяет отличать их
 * от метрик других кэшей (например, Redis) в Grafana.
 * <p>
 * <b>Почему Caffeine, а не HashMap?</b>
 * <ul>
 *   <li>Контроль размера — защита от OutOfMemoryError</li>
 *   <li>Интеллектуальное вытеснение (TinyLFU) — сохраняет самые «горячие» записи</li>
 *   <li>Автоматическое удаление по TTL — данные не застаиваются</li>
 *   <li>Single‑flight — при одновременных промахах только один поток обращается к БД</li>
 *   <li>Готовая интеграция с метриками — прозрачный мониторинг</li>
 * </ul>
 *
 * @see CaffeineConfig – конфигурация параметров кэша
 * @see OrderService – вызывающий сервис, использующий этот кэш
 */
@Service
@Slf4j
public class CaffeineOrderCacheService {

    /**
     * Инстанс Caffeine-кэша, созданный в {@link CaffeineConfig}.
     * Хранит пары (id заказа → объект Order) с учётом всех настроек:
     * максимальный размер, TTL, статистика и т.д.
     */
    private final Cache<Long, OrderResponseDto> cache;

    /**
     * Репозиторий для загрузки данных из БД в случае промаха.
     * Используется только внутри функции загрузки {@code cache.get(id, key -> ...)}.
     */
    private final OrderRepository orderRepository;

    /**
     * Конструктор внедряет готовый бин кэша, репозиторий и реестр метрик.
     * <p>
     * После инициализации кэша регистрируем его в Micrometer, чтобы все статистики
     * были доступны через Actuator и Prometheus.
     *
     * @param cache           сконфигурированный бин Cache<Long, Order>
     * @param orderRepository репозиторий для загрузки из БД
     * @param meterRegistry   реестр метрик Spring Boot
     */
    public CaffeineOrderCacheService(Cache<Long, OrderResponseDto> cache,
                                     OrderRepository orderRepository,
                                     MeterRegistry meterRegistry) {
        this.cache = cache;
        this.orderRepository = orderRepository;

        // Регистрируем метрики Caffeine в Micrometer.
        // Первый параметр — реестр, второй — сам кэш, третий — префикс имени метрики,
        // затем произвольные теги (здесь cache_type=caffeine).
        CaffeineCacheMetrics.monitor(
                meterRegistry,
                cache,
                "order",
                "cache_type", "caffeine"
        );
    }

    /**
     * Получить заказ по идентификатору с использованием кэша.
     * <p>
     * <b>Алгоритм работы:</b>
     * <ol>
     *   <li>Метод вызывает {@code cache.get(id, mappingFunction)}.</li>
     *   <li>Caffeine атомарно проверяет наличие ключа:
     *       <ul>
     *         <li>Если ключ есть — возвращает значение (попадание).</li>
     *         <li>Если ключа нет — синхронно выполняет переданную функцию загрузки,
     *             сохраняет результат в кэш и возвращает его.</li>
     *       </ul>
     *   </li>
     *   <li>При конкурентных запросах одного и того же ключа (например, 10 параллельных
     *       вызовов {@code getOrder(1)}) функция загрузки будет выполнена только один раз.
     *       Остальные потоки будут ждать завершения загрузки и получат тот же результат.
     *       Это предотвращает «штурм» базы данных (cache stampede).</li>
     *   <li>Если загрузка завершается с исключением, оно пробрасывается вызывающему коду,
     *       а запись в кэш не сохраняется (чтобы не кэшировать ошибку).</li>
     *   <li>Если репозиторий вернул {@code Optional.empty()}, выбрасывается
     *       {@link NotFoundOrderException} — кэш не сохраняет {@code null}.</li>
     * </ol>
     * <p>
     * <b>Примечание по транзакционности:</b>
     * <p>
     * Метод аннотирован {@code @Transactional(readOnly = true)} в вызывающем
     * {@link OrderService#getOrderWithItems(Long)}. Это гарантирует, что загрузка
     * из БД выполняется в рамках read‑only транзакции, что оптимизирует работу
     * с Hibernate (не отслеживает изменения) и снижает нагрузку.
     *
     * @param id идентификатор заказа
     * @return объект Order (никогда {@code null})
     * @throws NotFoundOrderException если заказ с таким id не найден в БД
     */
    public OrderResponseDto getOrder(Long id) {
        // cache.get() — атомарная операция «получить или вычислить».
        // Второй аргумент — лямбда, которая вызывается только при промахе.
        return cache.get(id, key -> {
            // Этот блок выполняется только если ключа в кэше нет.
            log.debug("Caffeine miss for order {}, loading from DB", key);

            // Загружаем данные из БД через репозиторий.
            // Используем findWithItemsById, чтобы подгрузить связанные сущности (OrderItem)
            // одним запросом (JOIN FETCH) — это решает проблему N+1.
            return OrderResponseDto.from(orderRepository.findWithItemsById(key)
                    // Если заказ не найден — бросаем исключение, которое не будет закэшировано.
                    .orElseThrow(() -> new NotFoundOrderException("Order not found: " + key))
            );
        });
    }

    /**
     * Принудительно удалить запись из кэша (инвалидация).
     * <p>
     * Вызывается после обновления заказа (изменение статуса, отмена и т.п.),
     * чтобы следующий {@link #getOrder(Long)} загрузил свежие данные из БД.
     * <p>
     * <b>Почему это важно:</b>
     * <ul>
     *   <li>Без инвалидации пользователь мог бы видеть устаревший статус заказа.</li>
     *   <li>TTL защищает от «вечных» устаревших данных, но не гарантирует
     *       мгновенную актуальность после обновления.</li>
     *   <li>Ручная инвалидация даёт строгую согласованность на уровне кэша.</li>
     * </ul>
     * <p>
     * Метод потокобезопасен — {@code invalidate} атомарно удаляет запись,
     * если она существует.
     *
     * @param id идентификатор заказа, который нужно удалить из кэша
     */
    public void evict(Long id) {
        cache.invalidate(id);
        log.debug("Evicted order {} from caffeine cache", id);
    }
}