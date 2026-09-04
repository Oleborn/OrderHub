package oleborn.reactivenotificationservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.reactivenotificationservice.client.NotificationProviderClient;
import oleborn.reactivenotificationservice.model.dto.OrderCreatedEvent;
import oleborn.reactivenotificationservice.model.dto.ProviderResponse;
import oleborn.reactivenotificationservice.model.entity.NotificationSent;
import oleborn.reactivenotificationservice.repository.NotificationSentRepository;
import oleborn.reactivenotificationservice.service.NotificationProcessingService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;

/**
 * Реализация сервиса обработки уведомлений.
 * <p>
 * Содержит всю бизнес-логику, связанную с отправкой уведомлений:
 * <ul>
 *   <li>Вызов внешнего провайдера через {@link NotificationProviderClient}</li>
 *   <li>Обработка успешного/неуспешного ответа</li>
 *   <li>Повторные попытки (retry) при временных ошибках</li>
 *   <li>Fallback (запасной вариант) при исчерпании попыток</li>
 *   <li>Сохранение результата в БД через {@link NotificationSentRepository}</li>
 * </ul>
 * <p>
 * Все операции неблокирующие и возвращают {@link Mono}.
 * <p>
 * Важный architectural момент: сервис НЕ знает о Kafka, consumer'ах и offset'ах.
 * Он работает только с доменной моделью (OrderCreatedEvent) и возвращает
 * реактивный сигнал о завершении. Это обеспечивает слабую связанность и
 * возможность переиспользования (например, из REST-контроллера или по расписанию).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationProcessingServiceImpl implements NotificationProcessingService {

    private final NotificationProviderClient providerClient;
    private final NotificationSentRepository repository;

    /**
     * Максимальное количество попыток отправки уведомления.
     * Если провайдер временно недоступен или возвращает ошибку,
     * мы повторим попытку до 3 раз.
     */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Начальная задержка между повторными попытками.
     * Используется экспоненциальный бэкофф: 1с → 2с → 4с
     */
    private static final Duration RETRY_BACKOFF_MIN = Duration.ofSeconds(1);

    /**
     * Обрабатывает уведомление для созданного заказа.
     * <p>
     * Весь процесс строится как реактивная цепочка операций:
     * <ol>
     *   <li>Вызов провайдера ({@link NotificationProviderClient#sendNotification})</li>
     *   <li>Обработка ответа ({@link #handleProviderResponse})</li>
     *   <li>Повторные попытки при ошибках ({@link Retry#backoff})</li>
     *   <li>Fallback при исчерпании попыток ({@link #handleFallback})</li>
     * </ol>
     * <p>
     * Важно: цепочка ВСЕГДА завершается успешно (благодаря fallback),
     * поэтому вызывающий код (consumer) может безопасно подтверждать offset.
     *
     * @param event событие создания заказа
     * @return Mono<Void> — сигнал о завершении обработки (всегда успешный)
     */
    @Override
    public Mono<Void> processNotification(OrderCreatedEvent event) {

        Long orderId = event.orderId();
        log.debug("Обработка уведомления для заказа: {}", orderId);

        // Шаг 1: Вызов провайдера
        // Возвращает Mono<ProviderResponse>
        return providerClient.sendNotification(event)

                // retryWhen — перехватывает ошибки и повторяет ВСЮ цепочку (начиная с
                // providerClient.sendNotification()), но не более MAX_RETRY_ATTEMPTS раз.
                //
                // Важно: retryWhen расположен ДО onErrorResume!
                // Если бы мы поставили onErrorResume перед retryWhen, то ошибка была бы
                // заменена на fallback, и retryWhen её бы не увидел.
                //
                // Retry.backoff(3, Duration.ofSeconds(1)) — это стандартная стратегия
                // с экспоненциальной задержкой:
                //   - 1-я попытка: задержка 1 секунда
                //   - 2-я попытка: задержка 2 секунды
                //   - 3-я попытка: задержка 4 секунды
                // После 3-х неудачных попыток ошибка пробрасывается дальше → к onErrorResume.
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_BACKOFF_MIN))

                // flatMap — трансформирует успешный Mono<ProviderResponse> в Mono<Void>.
                // Если провайдер вернул success=true → сохраняем "SENT".
                // Если success=false → генерируем ошибку (которая будет перехвачена retry).
                .flatMap(response -> handleProviderResponse(orderId, response))

                // onErrorResume — перехватывает ошибку ПОСЛЕ того, как все попытки retry
                // исчерпаны. Вместо того чтобы уронить поток, мы выполняем fallback:
                // сохраняем статус "FAILED" в БД и завершаем Mono успешно (без ошибки).
                .onErrorResume(e -> handleFallback(orderId, e))

                // then() — отбрасывает результат предыдущей операции (ProviderResponse)
                // и возвращает Mono<Void>. Это сигнал о том, что операция завершена,
                // но не несёт никакого значения.
                //
                // Это важно для consumer'а: он ожидает Mono<Void>, чтобы подписаться
                // и продолжить управление потоком.
                .then();
    }

    /**
     * Обрабатывает успешный или неуспешный ответ от провайдера.
     * <p>
     * Если ответ успешный → сохраняем статус "SENT" и завершаем Mono успешно.
     * Если ответ неуспешный → генерируем ошибку, которая попадёт в retry.
     *
     * @param orderId  идентификатор заказа
     * @param response ответ от провайдера
     * @return Mono<Void> — успешный (SENT) или с ошибкой (для retry)
     */
    private Mono<Void> handleProviderResponse(Long orderId, ProviderResponse response) {

        if (response.success()) {
            log.info("Уведомление успешно отправлено для заказа {}", orderId);

            // Успех → сохраняем в БД и завершаем Mono
            return saveNotificationResult(orderId, "SENT");

        } else {

            // Провайдер вернул failure — это рассматривается как ошибка,
            // чтобы активировать механизм retry
            log.warn("Провайдер вернул ошибку для заказа {}: {}", orderId, response.message());

            return Mono.error(new RuntimeException("Ошибка провайдера: " + response.message()));

        }
    }

    /**
     * Сохраняет результат обработки уведомления в базу данных.
     * <p>
     * Используется как для успешной отправки (SENT), так и для fallback (FAILED).
     * <p>
     * Важно: это НЕБЛОКИРУЮЩАЯ операция благодаря R2DBC.
     * Репозиторий возвращает Mono, а не блокирует поток на время записи.
     *
     * @param orderId идентификатор заказа
     * @param status  статус отправки ("SENT" или "FAILED")
     * @return Mono<Void> — сигнал о завершении сохранения
     */
    private Mono<Void> saveNotificationResult(Long orderId, String status) {

        // Создаём сущность для сохранения
        NotificationSent entity = NotificationSent.builder()
                .orderId(orderId)
                .status(status)
                .sentAt(Instant.now())
                .build();

        // repository.save(entity) возвращает Mono<NotificationSent>
        // doOnSuccess — побочный эффект для логирования (не влияет на поток)
        // then() — отбрасывает результат и возвращает Mono<Void>
        return repository.save(entity)
                .doOnSuccess(saved -> log.debug("Сохранена запись для заказа {} со статусом {}", orderId, status))
                .then();
    }

    /**
     * Fallback (запасной вариант) при исчерпании всех попыток retry.
     * <p>
     * Сохраняет статус "FAILED" в БД и завершает Mono успешно.
     * Это гарантирует, что consumer всегда получит успешный сигнал
     * и сможет подтвердить offset, не теряя сообщение.
     * <p>
     * Важно: здесь мы НЕ пробрасываем ошибку дальше — мы её «проглатываем»
     * и заменяем на успешное завершение с fallback-результатом.
     *
     * @param orderId идентификатор заказа
     * @param error   ошибка, которая привела к fallback
     * @return Mono<Void> — всегда успешный (с записью FAILED в БД)
     */
    private Mono<Void> handleFallback(Long orderId, Throwable error) {

        log.error("Все попытки отправки уведомления для заказа {} исчерпаны, сохраняем как FAILED", orderId, error);

        return saveNotificationResult(orderId, "FAILED");
    }
}