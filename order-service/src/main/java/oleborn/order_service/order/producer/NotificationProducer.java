package oleborn.order_service.order.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.config.ProducerRabbitConfig;
import oleborn.order_service.order.domain.dto.OrderCreatedEvent;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendOrderCreatedEvent(OrderCreatedEvent event) {

        // 1. Создаём корреляционные данные — уникальный идентификатор этого сообщения
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        // 2. Логируем факт отправки, чтобы можно было сопоставить с confirm/return
        log.info("Sending OrderCreatedEvent: orderId={}, correlationId={}", event.orderId(), correlationData.getId());

        // 3. Отправляем сообщение в exchange с routing key и корреляцией
        rabbitTemplate.convertAndSend(
                ProducerRabbitConfig.EXCHANGE_NOTIFICATIONS,   // exchange
                ProducerRabbitConfig.ROUTING_KEY_NOTIFICATIONS, // routing key
                event,                                          // payload
                correlationData                                 // для confirms
        );
    }
}
