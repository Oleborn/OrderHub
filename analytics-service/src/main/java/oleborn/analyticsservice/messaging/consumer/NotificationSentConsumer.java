package oleborn.analyticsservice.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.analyticsservice.domain.event.NotificationSentEvent;
import oleborn.analyticsservice.service.OrderLifecycleService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationSentConsumer {

    private final OrderLifecycleService service;

    @KafkaListener(topics = "${app.topic.notification-events}", groupId = "analytics-service-group")
    public void onNotificationSent(NotificationSentEvent event, Acknowledgment ack) {

        try {

            log.info("Received NotificationSent: orderId={}, timestamp={}", event.orderId(), event.timestamp());

            service.upsertNotificationSentAt(event.orderId(), event.timestamp());

            ack.acknowledge();

            log.debug("NotificationSent processed for order {}", event.orderId());

        } catch (Exception e) {
            log.error("Error processing NotificationSent for order {}", event.orderId(), e);
            throw e;
        }
    }
}