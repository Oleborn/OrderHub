package oleborn.order_service.order.messaging.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.event.NotificationEvent;
import oleborn.order_service.order.domain.event.OrderCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationProducer {

    @Value("${app.topic.notification-events}")
    private String notificationEventsTopic;

    private final KafkaTemplate<String, Object> reliableKafkaTemplate;

    public void sendOrderUpdatedEvent(NotificationEvent event) {

        String key = String.valueOf(event.orderId());

        CompletableFuture<SendResult<String, Object>> future =
                reliableKafkaTemplate.send(
                        notificationEventsTopic,
                        key,
                        event
                );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Событие отправлено в topic: {}, partition: {}, offset: {}",
                        notificationEventsTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            } else {
                log.error("Ошибка отправки события по orderId: {}", event.orderId(), ex);
                // Здесь можно сохранить в outbox
            }
        });
    }
}