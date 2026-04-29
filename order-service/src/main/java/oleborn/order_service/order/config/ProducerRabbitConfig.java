package oleborn.order_service.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ProducerRabbitConfig {

    public static final String EXCHANGE_NOTIFICATIONS = "notifications.exchange";
    public static final String ROUTING_KEY_NOTIFICATIONS = "notifications.queue";

    @Bean
    public DirectExchange notificationsExchange() {
        return new DirectExchange(EXCHANGE_NOTIFICATIONS, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);

        // Publisher Confirms — асинхронное подтверждение от брокера
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("Message not confirmed. CorrelationId: {}, cause: {}", correlationData != null ? correlationData.getId() : "null", cause);
                // Здесь — сохранение в persistent storage для повтора
            } else {
                log.debug("Message confirmed. CorrelationId: {}",
                        correlationData != null ? correlationData.getId() : "null");
            }
        });

        // Publisher Returns — когда сообщение не доставлено ни в одну очередь
        template.setReturnsCallback(returned -> {
            log.error("Message returned: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });

        template.setMandatory(true);               // без этого возвраты не работают
        template.setUsePublisherConnection(true);  // выделенное соединение для confirms

        return template;
    }
}