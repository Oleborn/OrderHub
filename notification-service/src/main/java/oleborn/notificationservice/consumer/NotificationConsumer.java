package oleborn.notificationservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oleborn.notificationservice.config.ConsumerRabbitConfig;
import oleborn.notificationservice.event.OrderCreatedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {

    private final ObjectMapper objectMapper;

    @RabbitListener(
            // ===== ОСНОВНЫЕ: КАКИЕ ОЧЕРЕДИ СЛУШАТЬ =====
            id = "notificationConsumer",                              // Уникальный ID контейнера (для управления)
            containerFactory = "rabbitListenerContainerFactory",      // Фабрика контейнеров (по умолчанию)
            queues = ConsumerRabbitConfig.QUEUE_NOTIFICATIONS,        // Имена очередей (можно массив)
            // queuesToDeclare = {},                                  // Очереди, которые нужно объявить прямо здесь
            bindings = {},                                            // Привязки, если объявляем очереди в аннотации
            // group = "notifications-group",                         // Группа потребителей (для конкурирующих consumer'ов)

            // ===== АДМИНИСТРАТИВНЫЕ =====
            autoStartup = "true",                                     // Автозапуск контейнера (по умолчанию true)
            // admin = "rabbitAdmin",                                 // Бин RabbitAdmin (если несколько)

            // ===== ПОДТВЕРЖДЕНИЕ (ACK) =====
            ackMode = "MANUAL",                                         // Режим подтверждения: AUTO / MANUAL / NONE

            // ===== КОНКУРЕНТНОСТЬ =====
            concurrency = "3",                                        // Мин. число потребителей (потоков)
            // maxConcurrency = "10",                                 // Макс. число потоков (можно в YAML)

            // ===== ПРЕДВЫБОРКА =====
            // prefetch = "10",                                       // Prefetch count (можно в YAML)

            // ===== ПАКЕТНАЯ ОБРАБОТКА =====
            // batchSize = "50",                                      // Размер пакета для batch-обработки

            // ===== ОБРАБОТКА ОШИБОК =====
            errorHandler = "rabbitListenerErrorHandler",              // Бин обработчика ошибок
            // returnExceptions = "false",                            // Пробрасывать исключения в вызывающий код

            // ===== УПРАВЛЕНИЕ СОЕДИНЕНИЕМ =====
            exclusive = false                                         // Эксклюзивный доступ к очереди
            // priority = "0",                                        // Приоритет consumer'а

            // ===== ОТВЕТНЫЕ СООБЩЕНИЯ (RPC — НЕ ИСПОЛЬЗУЕМ) =====
            // replyContentType = "application/json",                 // Content-Type ответа
            // replyPostProcessor = "replyPostProcessor",             // Постобработчик ответа

            // ===== ДОПОЛНИТЕЛЬНЫЕ =====
            // messageConverter = "messageConverter",                 // Конвертер сообщений (по умолчанию Jackson2Json)
            // executor = "taskExecutor",                             // Исполнитель потоков
            // contentType = "application/json"                       // Ожидаемый Content-Type
    )

    @SneakyThrows
    public void handle(byte[] body,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {

        log.info("Приняты сырые данные");

        OrderCreatedEvent event = objectMapper.readValue(body, OrderCreatedEvent.class);

        try {

            log.info("Принято событие из RabbitMQ: orderId={}", event.orderId());

            //для демонстрации наращивания очереди
            //Thread.sleep(2000);

            // для демонстрации отправки сообщений в DLQ
//            throw new RuntimeException("GO EXCEPTION");

            channel.basicAck(deliveryTag, false);
            log.info("Acknowledged: orderId={}", event.orderId());

            log.info("Email по заказу: orderId={}, успешно отправлен", event.orderId());

        } catch (Exception e) {
            log.error("Processing failed: orderId={}, error={}", event.orderId(), e.getMessage());

            // Отклоняем без возврата в очередь: poison → DLQ
            // Параметры: deliveryTag, multiple, requeue
            channel.basicNack(deliveryTag, false, false);
            // requeue=false → сообщение уйдёт в DLX/DLQ
        }
    }

    //пример с ненастроенной сериализацией/ошибка
//    public void handle(OrderCreatedEvent event) {
//        log.info("Принято событие из RabbitMQ: orderId={}", event.orderId());
//
//        log.info("Email по заказу: orderId={}, успешно отправлен", event.orderId());
//    }
}