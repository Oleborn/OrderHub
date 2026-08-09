package oleborn.order_service.order.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import oleborn.order_service.order.domain.dto.OrderResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Конфигурация локального кэша Caffeine для заказов.
 * <p>
 * Все настройки вынесены в properties для гибкости (можно переопределять через
 * application.yaml или переменные окружения).
 */
@Configuration
public class CaffeineConfig {

    // ------------------- Базовые параметры -------------------

    @Value("${caffeine.order.initialCapacity:16}")
    private int initialCapacity;           // начальная ёмкость (по умолчанию 16)

    @Value("${caffeine.order.maximumSize:1000}")
    private long maximumSize;              // максимальное количество записей

    @Value("${caffeine.order.expireAfterWriteMinutes:1}")
    private long expireAfterWriteMinutes;  // TTL (в минутах) — можно вынести как Duration

    // ------------------- Дополнительные параметры (закомментированы, но доступны) -------------------

    // @Value("${caffeine.order.expireAfterAccessMinutes:0}")
    // private long expireAfterAccessMinutes; // TTL от последнего доступа

    // @Value("${caffeine.order.refreshAfterWriteMinutes:0}")
    // private long refreshAfterWriteMinutes; // асинхронное обновление

    // @Value("${caffeine.order.maximumWeight:0}")
    // private long maximumWeight;            // максимальный вес (если используется Weigher)

    // @Value("${caffeine.order.weakKeys:false}")
    // private boolean weakKeys;              // слабые ссылки на ключи

    // @Value("${caffeine.order.weakValues:false}")
    // private boolean weakValues;            // слабые ссылки на значения

    // @Value("${caffeine.order.softValues:false}")
    // private boolean softValues;            // мягкие ссылки на значения

    // ---------- Слушатели, исполнители, планировщики, счётчики ----------

    // Можно внедрить кастомный Executor, например из пула приложения
    // @Value("${caffeine.order.executor:ForkJoinPool.commonPool()}")
    // private Executor executor;

    // Можно внедрить кастомный Scheduler для фонового удаления записей
    // @Value("${caffeine.order.scheduler:#{null}}")
    // private Scheduler scheduler;

    /**
     * Создаёт бин Cache<Long, Order> со всеми необходимыми настройками.
     * <p>
     * Используем максимально возможный набор параметров для демонстрации.
     * В реальном проекте выбирайте только те, которые нужны.
     */
    @Bean
    public Cache<Long, OrderResponseDto> orderCache() {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();

        // ----- 1. Размер и производительность -----
        builder.initialCapacity(initialCapacity);
        builder.maximumSize(maximumSize);

        // ----- 2. Время жизни -----
        // expireAfterWrite – запись удаляется через заданное время после создания/обновления
        builder.expireAfterWrite(Duration.ofMinutes(expireAfterWriteMinutes));

        // Альтернативы (раскомментируйте при необходимости):
        // builder.expireAfterAccess(Duration.ofMinutes(expireAfterAccessMinutes));
        // builder.refreshAfterWrite(Duration.ofMinutes(refreshAfterWriteMinutes));

        // ----- 3. Вариант с весом (если записи имеют разный вес) -----
        // if (maximumWeight > 0) {
        //     builder.maximumWeight(maximumWeight);
        //     builder.weigher((Long key, Order value) -> {
        //         // Например, вес = количество позиций в заказе + 1
        //         return value.getItems() == null ? 1 : value.getItems().size() + 1;
        //     });
        // }

        // ----- 4. Слабые/мягкие ссылки (для работы с GC) -----
        // if (weakKeys) builder.weakKeys();
        // if (weakValues) builder.weakValues();
        // if (softValues) builder.softValues();

        // ----- 5. Статистика (обязательно) -----
        builder.recordStats();

        // ----- 6. Слушатель удалений (для логирования/метрик) -----
        builder.removalListener((Long key, OrderResponseDto value, RemovalCause cause) -> {
            // Можно залогировать или отправить метрику
            // Например, счётчик удалений по причине (expired, evicted, replaced...)
        });

        // ----- 7. Слушатель вытеснения (отдельно от обычных удалений) -----
        // builder.evictionListener((key, value, cause) -> { ... });

        // ----- 8. Исполнитель для асинхронных задач (по умолчанию ForkJoinPool.commonPool()) -----
        // builder.executor(ForkJoinPool.commonPool());

        // ----- 9. Планировщик для периодической очистки (по умолчанию disabled) -----
        // builder.scheduler(Scheduler.systemScheduler()); // или Scheduler.disabledScheduler()

        // ----- 10. Кастомный Expiry (для сложной логики времени жизни) -----
        // builder.expireAfter(new Expiry<Long, Order>() {
        //     @Override
        //     public long expireAfterCreate(Long key, Order value, long currentTime) {
        //         // Например, для заказов со статусом PAID срок жизни больше
        //         return value.getStatus() == OrderStatus.PAID
        //                 ? Duration.ofMinutes(10).toNanos()
        //                 : Duration.ofMinutes(1).toNanos();
        //     }
        //     @Override
        //     public long expireAfterUpdate(...) { ... }
        //     @Override
        //     public long expireAfterRead(...) { ... }
        // });

        return builder.build();
    }
}