package oleborn.notificationservice.config;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ConsumerRabbitConfig {

    public static final String QUEUE_NOTIFICATIONS = "notifications.queue";
    public static final String QUEUE_NOTIFICATIONS_DLQ = "notifications.queue.dlq";
    public static final String QUEUE_NOTIFICATIONS_RETRY = "notifications.retry";
    public static final String EXCHANGE_NOTIFICATIONS = "notifications.exchange";
    public static final String DLX_EXCHANGE = "notifications.dlx";
    public static final String ROUTING_KEY_NOTIFICATIONS = "notifications.queue";
    public static final String ROUTING_KEY_DLQ = "notifications.queue.dlq";

    // ===== Обменники (для автономности) =====
    @Bean
    public DirectExchange notificationsExchange() {
        return new DirectExchange(EXCHANGE_NOTIFICATIONS, true, false);
    }

    @Bean
    public DirectExchange notificationsDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    // ===== Очереди =====
    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLQ)
                .withArgument("x-message-ttl", 3_600_000)      // 1 час
                .withArgument("x-max-length", 10_000)          // макс длина
                .withArgument("x-overflow", "reject-publish")  // при переполнении – отказ
                .build();
    }

    @Bean
    public Queue notificationsDlq() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS_DLQ).build();
    }

    @Bean
    public Queue notificationsRetryQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS_RETRY)
                .withArgument("x-dead-letter-exchange", EXCHANGE_NOTIFICATIONS)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_NOTIFICATIONS)
                .withArgument("x-message-ttl", 30_000) // 30 секунд задержки
                .build();
    }

    // ===== Привязки =====
    @Bean
    public Binding notificationsBinding() {
        return BindingBuilder.bind(notificationsQueue())
                .to(notificationsExchange())
                .with(ROUTING_KEY_NOTIFICATIONS);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(notificationsDlq())
                .to(notificationsDlxExchange())
                .with(ROUTING_KEY_DLQ);
    }

    @Bean
    public RabbitListenerErrorHandler rabbitListenerErrorHandler() {
        return (
                Message amqpMessage,
                Channel channel,                                            // AMQP-сообщение (сырое)
                org.springframework.messaging.Message<?> springMessage,     // Spring-сообщение (конвертированное)
                ListenerExecutionFailedException exception
        ) -> {            // Исключение-обёртка

            // Причина ошибки — внутри ListenerExecutionFailedException
            Throwable rootCause = exception.getCause() != null
                    ? exception.getCause()
                    : exception;

            log.error("Ошибка обработки сообщения: messageId={}, routingKey={}, errorType={}, errorMessage={}",
                    amqpMessage.getMessageProperties().getMessageId(),
                    amqpMessage.getMessageProperties().getReceivedRoutingKey(),
                    rootCause.getClass().getSimpleName(),
                    rootCause.getMessage()
            );

            // ===== Гибкая логика обработки ошибок =====

            // Пример: некоторые ошибки сразу отправляем в DLQ без retry
            // if (rootCause instanceof InvalidEventException) {
            //     throw new AmqpRejectAndDontRequeueException("Invalid event data", rootCause);
            // }

            // Пример: временные ошибки requeue'им
            // if (rootCause instanceof MailServerUnavailableException) {
            //     throw rootCause; // Spring Retry отработает, потом requeue/DLQ
            // }

            // По умолчанию: пробрасываем исключение → срабатывает retry → DLQ
            throw exception;
        };
    }
}