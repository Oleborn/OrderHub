package oleborn.reactivenotificationservice.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.reactivenotificationservice.config.KafkaReceiverConfig;
import oleborn.reactivenotificationservice.model.dto.OrderCreatedEvent;
import oleborn.reactivenotificationservice.service.NotificationProcessingService;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Реактивный потребитель сообщений из Kafka.
 * <p>
 * В отличие от блокирующего @KafkaListener, этот consumer работает на основе
 * Reactive Streams, что даёт:
 * <ul>
 *   <li>Полный контроль над demand (backpressure)</li>
 *   <li>Неблокирующую обработку каждого сообщения</li>
 *   <li>Возможность ограничивать параллелизм через flatMap(concurrency)</li>
 *   <li>Graceful shutdown через SmartLifecycle</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReactiveKafkaConsumer implements SmartLifecycle {

    /**
     * Реактивный Kafka receiver — обёртка над стандартным consumer'ом.
     * Предоставляет реактивный поток {@code Flux<ReceiverRecord>}.
     * Создан в {@link KafkaReceiverConfig}.
     */
    private final KafkaReceiver<String, OrderCreatedEvent> receiver;

    private final NotificationProcessingService processingService;

    /**
     * Максимальное количество сообщений, обрабатываемых параллельно.
     * Это основной механизм управления demand (backpressure).
     * 10 — хороший баланс между производительностью и ресурсами.
     * Значение подбирается исходя из:
     *   - лимита провайдера (50 RPS)
     *   - средней латентности вызова (200-500 мс)
     *   - доступных ресурсов (CPU, память)
     */
    private static final int CONCURRENCY = 50;

    /**
     * Ссылка на подписку (Disposable).
     * Нужна для:
     *   - проверки, запущен ли consumer (isRunning)
     *   - корректной остановки (dispose при shutdown)
     */
    private Disposable subscription;

    /**
     * Запускает реактивный пайплайн.
     * <p>
     * Важно: метод вызывается Spring'ом автоматически после полной инициализации
     * всех бинов, в отличие от @PostConstruct, который вызывается во время
     * создания бина (до готовности контекста).
     * <p>
     * Здесь мы подписываемся на поток из Kafka и запускаем обработку.
     */
    @Override
    public void start() {
        log.info("Reactive Kafka consumer начинает запуск...");

        this.subscription = receiver.receive()
                // --------------------------------------------------------------------
                // receiver.receive() — возвращает Flux<ReceiverRecord>
                // --------------------------------------------------------------------
                // Это горячий поток: он начинает читать из Kafka, как только на него
                // подписываются (subscribe). Однако чтение происходит с учётом demand
                // от downstream.
                //
                // ReceiverRecord содержит:
                //   - value() — само сообщение (OrderCreatedEvent)
                //   - receiverOffset() — объект для управления offset'ом
                //   - key(), partition(), offset() — метаданные

                // --------------------------------------------------------------------
                // .flatMap(this::handleRecord, CONCURRENCY) — ОСНОВНОЙ МЕХАНИЗМ УПРАВЛЕНИЯ
                // --------------------------------------------------------------------
                // flatMap преобразует каждый элемент Flux в другой Publisher (Mono)
                // и сливает результаты в один поток.
                //
                // Параметр CONCURRENCY (10) ограничивает количество одновременно
                // выполняющихся внутренних операций. Это создаёт:
                //
                //   1. BOUNDED CONCURRENCY — не более 10 сообщений обрабатываются
                //      параллельно в любой момент времени.
                //
                //   2. BACKPRESSURE (DEMAND) — если все 10 слотов заняты, flatMap
                //      перестаёт запрашивать новые элементы у receiver.receive().
                //      Как только один слот освобождается, flatMap запрашивает
                //      следующее сообщение.
                //
                // Это и есть реализация demand'а (request(n)) в реактивном пайплайне.
                // Мы не читаем все сообщения из Kafka в память — мы контролируем
                // скорость потребления через ограничение параллелизма.
                .flatMap(this::handleRecord, CONCURRENCY)

                // --------------------------------------------------------------------
                // .doOnError — ловит НЕОБРАБОТАННЫЕ ошибки
                // --------------------------------------------------------------------
                // Это безопасный слой: если какая-то ошибка не была перехвачена
                // внутри handleRecord (например, сбой в самом flatMap или receiver),
                // она попадёт сюда и будет залогирована.
                //
                // Важно: doOnError НЕ перехватывает ошибку — она всё равно
                // пробрасывается дальше, к retryWhen.
                .doOnError(e -> log.error("Необрабатываемая ошибка в реактивном пайплайне", e))

                // --------------------------------------------------------------------
                // .retryWhen(Retry.backoff(...)) — ПЕРЕЗАПУСК ПАЙПЛАЙНА
                // --------------------------------------------------------------------
                // Если весь пайплайн завершился с ошибкой (например, потеря соединения
                // с Kafka), retryWhen переподписывается на receiver.receive() и
                // перезапускает поток.
                //
                // Retry.backoff(3, Duration.ofSeconds(2)) означает:
                //   - максимум 3 попытки переподписки
                //   - задержка между попытками по экспоненте: 2с → 4с → 8с
                //   - после 3-й попытки ошибка пробрасывается дальше
                //
                // Это защита от временных сбоев инфраструктуры.
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))

                // --------------------------------------------------------------------
                // .subscribe() — ЗАПУСК ПАЙПЛАЙНА
                // --------------------------------------------------------------------
                // Без subscribe() ничего бы не происходило — реактивные цепочки ленивы.
                // subscribe() инициирует выполнение и возвращает Disposable,
                // который мы сохраняем для управления жизненным циклом.
                .subscribe();

        log.info("Reactive Kafka consumer успешно стартовал.");
    }

    /**
     * Останавливает consumer при завершении приложения (graceful shutdown).
     * <p>
     * .dispose() отменяет подписку, что:
     * <ul>
     *   <li>Останавливает приём новых сообщений</li>
     *   <li>Даёт завершиться текущим обработкам</li>
     * </ul>
     * После завершения всех обработок подписка закрывается полностью.
     */
    @Override
    public void stop() {
        if (this.subscription != null && !this.subscription.isDisposed()) {
            log.info("Начата graceful shutdown для Kafka consumer...");
            this.subscription.dispose();
        }
        log.info("Reactive Kafka consumer остановлен.");
    }

    /**
     * Проверяет, активен ли consumer.
     * Используется Spring'ом для управления жизненным циклом.
     */
    @Override
    public boolean isRunning() {
        return this.subscription != null && !this.subscription.isDisposed();
    }

    /**
     * Обрабатывает одну запись из Kafka.
     * <p>
     * Возвращает {@code Mono<Void>}, который сигнализирует о завершении обработки.
     * Этот Mono всегда завершается успешно (благодаря fallback в сервисе),
     * поэтому offset подтверждается в любом случае.
     * <p>
     * Важно: метод НЕ обрабатывает ошибки — они пробрасываются вверх
     * к flatMap, который их перехватывает (но в нашем случае ошибок не будет,
     * потому что сервис всегда завершается успешно через fallback).
     *
     * @param record запись из Kafka с событием OrderCreatedEvent
     * @return Mono<Void> — сигнал о завершении обработки
     */
    private Mono<Void> handleRecord(ReceiverRecord<String, OrderCreatedEvent> record) {

        // Извлечение данных из записи ---
        OrderCreatedEvent event = record.value();

        if (event == null) {

            log.warn("Получено повреждённое сообщение (десериализация не удалась), пропускаем offset: {}", record.offset());

            // Подтверждаем offset, чтобы не зацикливаться на этом сообщении
            record.receiverOffset().acknowledge();
            return Mono.empty(); // завершаем обработку без ошибки
        }

        Long orderId = event.orderId();
        log.debug("Отправлена запись для заказа {}", orderId);

        // Делегирование обработки сервису ---
        // processingService.processNotification(event) возвращает Mono<Void>,
        // который завершается успешно после того, как уведомление отправлено
        // (или fallback выполнен).
        return processingService.processNotification(event)

                // Подтверждение offset после обработки ---
                // doFinally выполняется ВСЕГДА — независимо от того, как завершился
                // Mono (успех, ошибка, отмена).
                //
                // Это гарантирует, что offset будет закоммичен даже если:
                //   - обработка упала с ошибкой (но мы перехватили её в сервисе)
                //   - обработка была отменена (например, при shutdown)
                //
                // Благодаря fallback в сервисе, doFinally всегда вызывается после
                // успешного завершения, поэтому offset подтверждается безопасно.
                //
                // Важно: acknowledge() здесь — это ручной коммит offset.
                // Он отправляет брокеру сигнал, что запись обработана, и её можно
                // удалить из consumer group offset'ов.
                .doFinally(signalType -> {

                    record.receiverOffset().acknowledge();

                    log.debug("Offset сдвинут для заказа {}", orderId);

                });
    }
}