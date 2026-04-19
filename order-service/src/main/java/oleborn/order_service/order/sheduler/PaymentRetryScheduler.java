package oleborn.order_service.order.sheduler;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.dictionary.OrderStatus;
import oleborn.order_service.order.domain.Order;
import oleborn.order_service.order.domain.dto.OrderCreatedEvent;
import oleborn.order_service.order.domain.dto.PaymentResponseDto;
import oleborn.order_service.order.repository.OrderRepository;
import oleborn.order_service.order.service.PaymentService;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    @Value("${payment.retry.max-attempts:3}")
    private Integer maxAttempts;

    @Value("${payment.retry.timeout:30}")
    private Long timeoutSeconds;

    @Value("${payment.retry.batch-size:500}")
    private Integer batchSize;

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    //для метрик
    private final MeterRegistry meterRegistry;

    // Храним состояние повторных попыток
    private final Map<Long, Integer> retryCounters = new ConcurrentHashMap<>();

    /**
     * Периодическое сканирование и обработка pending заказов.
     *
     * <p><b>Настройки расписания:</b>
     * <ul>
     *   <li><b>fixedDelayString</b> - задержка МЕЖДУ ОКОНЧАНИЕМ текущего и НАЧАЛОМ следующего выполнения.
     *       Используется fixedDelay (а не fixedRate) чтобы избежать наложения задач друг на друга,
     *       что критично при работе с БД и внешними сервисами.</li>
     *   <li><b>initialDelayString</b> - задержка перед ПЕРВЫМ запуском после старта приложения.
     *       Нужна чтобы дать приложению полностью инициализироваться (поднять контекст,
     *       установить соединения с БД, прогреть кэши) до начала обработки.</li>
     * </ul>
     *
     * <p><b>Значения берутся из конфигурации с возможностью переопределения:</b>
     * <pre>
     * payment.scanner.fixed-delay - интервал между запусками в миллисекундах (по умолчанию 30000 = 30 сек)
     * payment.scanner.initial-delay - начальная задержка в миллисекундах (по умолчанию 30000 = 30 сек)
     * </pre>
     *
     * <p><b>Альтернативные варианты настройки:</b>
     * <pre>
     * // Фиксированная задержка без возможности переопределения
     * &#64;Scheduled(fixedDelay = 30000, initialDelay = 30000)
     *
     * // CRON выражение для гибкой настройки (например разное расписание днём и ночью)
     * &#64;Scheduled(cron = "${payment.scanner.cron:0/30 * * * * ?}")
     * </pre>
     *
     * <p><b>Гарантии выполнения:</b>
     * <ul>
     *   <li>Если задача выполняется дольше интервала - следующий запуск ждёт завершения</li>
     *   <li>При возникновении исключения - задача логирует ошибку и продолжает работу по расписанию</li>
     *   <li>Для параллельного выполнения нужен отдельный пул потоков (см. SchedulingConfig)</li>
     * </ul>
     *
     * @see #processOrder(Order) - обработка одного заказа
     * @see #isOrderExpired(Order) - проверка устаревания заказа
     */

    @Scheduled(
            fixedDelayString = "${payment.scanner.fixed-delay:30000}",
            // задержка МЕЖДУ КОНЦОМ предыдущего и НАЧАЛОМ следующего выполнения
            // выбрана вместо fixedRate чтобы гарантировать, что задачи не накладываются

            initialDelayString = "${payment.scanner.initial-delay:30000}"
            // задержка перед ПЕРВЫМ запуском после старта приложения
            // даёт время на полную инициализацию контекста Spring
    )
    @Transactional //не забываем для работы с ленивой загрузкой
    public void scanAndProcessPendingOrders() {
        log.debug("Старт поиска pending заказов");

        Pageable limit = PageRequest.of(0, batchSize);
        // Находим все заказы в статусе PENDING
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING, limit);

        if (pendingOrders.isEmpty()) {
            log.debug("Нет pending заказов для обработки");
            return;
        }

        log.info("Найдено {} pending заказов", pendingOrders.size());

        // Обрабатываем каждый заказ
        for (Order order : pendingOrders) {
            try {
                processOrder(order);
            } catch (Exception e) {
                log.error("Ошибка при обработке заказа {}", order.getId(), e);
            }
        }
    }

    private void processOrder(Order order) {

        // Проверяем, не превышен ли лимит попыток
        int attempts = retryCounters.getOrDefault(order.getId(), 0);

        if (attempts >= maxAttempts) {
            log.debug("Заказ {} исчерпал все попытки ({}). Отмена.", order.getId(), maxAttempts);

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            retryCounters.remove(order.getId());
            recordMaxAttemptsExceededMetrics();

            return;
        }

        // Проверяем, не устарел ли заказ (например, старше 1 часа)
        if (isOrderExpired(order)) {
            log.warn("Заказ {} устарел (>1 часа). Отмена.", order.getId());

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            retryCounters.remove(order.getId());
            recordExpiredMetrics();

            return;
        }

        // Пытаемся оплатить
        log.debug("Попытка {}/{} оплаты заказа {}", attempts + 1, maxAttempts, order.getId());

        try {
            PaymentResponseDto response = paymentService
                    .processPaymentResilience4j(order)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            if (response.requiresPendingProcessing()) {
                // Сервис недоступен — увеличиваем счётчик попыток
                retryCounters.put(order.getId(), attempts + 1);
                log.warn("Заказ {} не оплачен (попытка {}). Сервис недоступен.", order.getId(), attempts + 1);

                recordPendingMetrics(attempts + 1);

            } else if (response.isSuccessful()) {

                try {
                    // УСПЕХ!
                    MDC.put("order_id", order.getId().toString());
                    MDC.put("total_amount", order.getTotalPrice().toString());
                    MDC.put("order_status", order.getStatus().toString());

                    order.setStatus(OrderStatus.PAID);
                    Order savedOrder = orderRepository.save(order);

                    log.info("Заказ {} УСПЕШНО оплачен с {}-й попытки", order.getId(), attempts + 1);

                    retryCounters.remove(order.getId());
                    recordSuccessMetrics(attempts + 1);

                    eventPublisher.publishEvent(OrderCreatedEvent.of(
                                    savedOrder.getId(),
                                    MDC.getCopyOfContextMap()
                            )
                    );
                } finally {
                    MDC.remove("order_id");
                    MDC.remove("total_amount");
                    MDC.remove("order_status");
                }

            } else {
                // Бизнес-ошибка (недостаточно средств и т.п.)
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);

                log.warn("Заказ {} отклонён: {}", order.getId(), response.message());

                retryCounters.remove(order.getId());
                recordBusinessErrorMetrics(response.message());
            }

        } catch (TimeoutException e) {
            // Таймаут при вызове
            retryCounters.put(order.getId(), attempts + 1);
            log.error("Таймаут для заказа {} (попытка {}): {}", order.getId(), attempts + 1, e.getMessage());

            //МЕТРИКА: техническая ошибка (таймаут)
            recordTechnicalErrorMetrics(attempts + 1, "TIMEOUT");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            retryCounters.put(order.getId(), attempts + 1);
            log.warn("Обработка заказа {} прервана (попытка {})", order.getId(), attempts + 1);

            //МЕТРИКА: техническая ошибка (прерывание)
            recordTechnicalErrorMetrics(attempts + 1, "INTERRUPTED");

        } catch (ExecutionException e) {
            retryCounters.put(order.getId(), attempts + 1);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Ошибка выполнения для заказа {} (попытка {}): {}",
                    order.getId(), attempts + 1, cause.getMessage());

            //МЕТРИКА: техническая ошибка (выполнение)
            recordTechnicalErrorMetrics(attempts + 1, "EXECUTION");

        } catch (Exception e) {
            log.error("Неожиданная ошибка для заказа {}", order.getId(), e);
            retryCounters.put(order.getId(), attempts + 1);

            //МЕТРИКА: техническая ошибка (неизвестная)
            recordTechnicalErrorMetrics(attempts + 1, "UNKNOWN");
        }
    }

    private boolean isOrderExpired(Order order) {
        // Заказы старше 1 часа считаем устаревшими
        return order.getCreateAt().plus(1, TimeUnit.HOURS.toChronoUnit()).isBefore(Instant.now());
    }

    private void recordTechnicalErrorMetrics(int attempts, String errorType) {
        meterRegistry.counter(
                "orders.payment.retry.technical_error_total",
                "attempts", String.valueOf(attempts),
                "error_type", errorType
        ).increment();
    }

    private void recordSuccessMetrics(int attempts) {
        meterRegistry.counter(
                "orders.payment.retry.success_total",
                "attempts", String.valueOf(attempts)
        ).increment();

        meterRegistry.summary("orders.payment.retry.attempts").record(attempts);
    }

    private void recordPendingMetrics(int attempts) {
        meterRegistry.counter(
                "orders.payment.retry.pending_total",
                "attempts", String.valueOf(attempts)
        ).increment();
    }

    private void recordMaxAttemptsExceededMetrics() {
        meterRegistry.counter("orders.payment.retry.max_attempts_exceeded_total").increment();
    }

    private void recordExpiredMetrics() {
        meterRegistry.counter("orders.payment.retry.expired_total").increment();
    }

    private void recordBusinessErrorMetrics(String errorMessage) {
        meterRegistry.counter(
                "orders.payment.retry.business_error_total",
                "error", errorMessage
        ).increment();
    }
}