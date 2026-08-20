package oleborn.analyticsservice.repository;

import oleborn.analyticsservice.domain.entity.OrderLifecycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Репозиторий для работы с таблицей order_lifecycle – read‑моделью жизненного цикла заказа.
 * <p>
 * Все методы используют паттерн UPSERT (INSERT ... ON CONFLICT DO UPDATE) для обеспечения
 * идемпотентности и защиты от неупорядоченных событий в асинхронной среде.
 * <p>
 * Ключевые принципы:
 * <ul>
 *   <li>Первое пришедшее событие создаёт строку (INSERT).</li>
 *   <li>Последующие обновляют только те поля, которые ещё не заполнены или пришли с более новым timestamp.</li>
 *   <li>Сравнение timestamp защищает от перезаписи более новых данных более старыми (защита от out‑of‑order).</li>
 *   <li>Использование COALESCE упрощает сравнение с NULL (в PostgreSQL NULL не сравним с операторами >, <).</li>
 * </ul>
 */
public interface OrderLifecycleRepository extends JpaRepository<OrderLifecycle, Long> {

    /**
     * Обновляет или вставляет время создания заказа (created_at).
     * <p>
     * Событие: OrderCreatedEvent.
     * <p>
     * Логика:
     * - Если записи нет – создаём новую с указанным order_id и created_at.
     * - Если запись уже есть – обновляем created_at только в том случае,
     *   если новое значение (EXCLUDED.created_at) больше существующего.
     *   Это предотвращает перезапись более позднего времени создания более ранним
     *   (например, если событие пришло с задержкой и дублируется).
     * <p>
     * Почему сложно: простой SET created_at = EXCLUDED.created_at перезаписал бы
     * существующее значение без проверки, что опасно при out‑of‑order.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO order_lifecycle (order_id, created_at)
            VALUES (:orderId, :createdAt)
            ON CONFLICT (order_id) DO UPDATE
            SET created_at = CASE
                WHEN EXCLUDED.created_at > order_lifecycle.created_at THEN EXCLUDED.created_at
                ELSE order_lifecycle.created_at
            END
            """, nativeQuery = true)
    void upsertCreatedAt(@Param("orderId") Long orderId, @Param("createdAt") Instant createdAt);

    /**
     * Обновляет или вставляет время начала обработки платежа (payment_started_at).
     * <p>
     * Событие: PaymentStartedEvent.
     * <p>
     * Логика аналогична upsertCreatedAt, но с использованием COALESCE для обработки NULL:
     * - Если поле payment_started_at ещё не заполнено (NULL), COALESCE подставляет '1970-01-01'
     *   (заведомо меньше любого реального timestamp), поэтому новое значение всегда будет больше
     *   и обновление произойдёт.
     * - Если поле уже имеет значение, обновляем только если новое значение больше существующего.
     * <p>
     * Почему COALESCE: в PostgreSQL сравнение с NULL даёт NULL, а не FALSE, поэтому условие
     * EXCLUDED.payment_started_at > NULL не сработает. COALESCE превращает NULL в «минимальную» дату,
     * чтобы сравнение работало корректно.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO order_lifecycle (order_id, payment_started_at)
            VALUES (:orderId, :startedAt)
            ON CONFLICT (order_id) DO UPDATE
            SET payment_started_at = CASE
                WHEN EXCLUDED.payment_started_at > COALESCE(order_lifecycle.payment_started_at, '1970-01-01')
                THEN EXCLUDED.payment_started_at
                ELSE order_lifecycle.payment_started_at
            END
            """, nativeQuery = true)
    void upsertPaymentStartedAt(@Param("orderId") Long orderId, @Param("startedAt") Instant startedAt);

    /**
     * Обновляет или вставляет время завершения платежа (payment_completed_at) и статус платежа (payment_status).
     * <p>
     * Событие: PaymentCompletedEvent.
     * <p>
     * Логика:
     * - Если записи нет – создаём с указанными order_id, payment_completed_at, payment_status.
     * - Если запись уже есть, обновляем оба поля, но только если новое значение payment_completed_at
     *   больше существующего (или существующее NULL). Тогда:
     *     * payment_completed_at обновляется на новое время.
     *     * payment_status обновляется на новый статус (обычно 'PAID').
     *   В противном случае оба поля остаются без изменений.
     * <p>
     * Почему статус обновляется вместе со временем: статус привязан к моменту завершения,
     * поэтому если пришло более старое событие (с меньшим timestamp), мы не должны менять
     * статус на тот, который был в прошлом.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO order_lifecycle (order_id, payment_completed_at, payment_status)
            VALUES (:orderId, :completedAt, :status)
            ON CONFLICT (order_id) DO UPDATE
            SET
                payment_completed_at = CASE
                    WHEN EXCLUDED.payment_completed_at > COALESCE(order_lifecycle.payment_completed_at, '1970-01-01')
                    THEN EXCLUDED.payment_completed_at
                    ELSE order_lifecycle.payment_completed_at
                END,
                payment_status = CASE
                    WHEN EXCLUDED.payment_completed_at > COALESCE(order_lifecycle.payment_completed_at, '1970-01-01')
                         OR order_lifecycle.payment_completed_at IS NULL
                    THEN EXCLUDED.payment_status
                    ELSE order_lifecycle.payment_status
                END
            """, nativeQuery = true)
    void upsertPaymentCompletedAt(@Param("orderId") Long orderId,
                                  @Param("completedAt") Instant completedAt,
                                  @Param("status") String status);

    /**
     * Обновляет или вставляет время отправки уведомления (notification_sent_at).
     * <p>
     * Событие: NotificationSentEvent.
     * <p>
     * Логика полностью аналогична upsertPaymentStartedAt – использует COALESCE для корректного
     * сравнения с NULL и защищает от перезаписи более новых данных более старыми.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO order_lifecycle (order_id, notification_sent_at)
            VALUES (:orderId, :sentAt)
            ON CONFLICT (order_id) DO UPDATE
            SET notification_sent_at = CASE
                WHEN EXCLUDED.notification_sent_at > COALESCE(order_lifecycle.notification_sent_at, '1970-01-01')
                THEN EXCLUDED.notification_sent_at
                ELSE order_lifecycle.notification_sent_at
            END
            """, nativeQuery = true)
    void upsertNotificationSentAt(@Param("orderId") Long orderId, @Param("sentAt") Instant sentAt);

    // ======================== АГРЕГАЦИОННЫЕ ЗАПРОСЫ ДЛЯ АНАЛИТИКИ ========================

    /**
     * Возвращает среднее время обработки платежа (в секундах) для всех успешно завершённых платежей.
     * <p>
     * Вычисляется как AVG(payment_completed_at - payment_started_at).
     * Учитываются только записи, где оба поля не NULL.
     * <p>
     * Используется в API /api/analytics/metrics/processing-times.
     */
    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (payment_completed_at - payment_started_at)))
            FROM order_lifecycle
            WHERE payment_completed_at IS NOT NULL AND payment_started_at IS NOT NULL
            """, nativeQuery = true)
    Double getAvgPaymentProcessingTime();

    /**
     * Возвращает среднее время отправки уведомления после завершения платежа (в секундах).
     * <p>
     * Вычисляется как AVG(notification_sent_at - payment_completed_at).
     * Учитываются только записи, где оба поля не NULL.
     * <p>
     * Используется в API /api/analytics/metrics/processing-times.
     */
    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (notification_sent_at - payment_completed_at)))
            FROM order_lifecycle
            WHERE notification_sent_at IS NOT NULL AND payment_completed_at IS NOT NULL
            """, nativeQuery = true)
    Double getAvgNotificationTime();
}