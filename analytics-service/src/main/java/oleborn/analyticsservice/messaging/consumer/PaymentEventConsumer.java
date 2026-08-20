package oleborn.analyticsservice.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.analyticsservice.domain.event.PaymentCompletedEvent;
import oleborn.analyticsservice.domain.event.PaymentStartedEvent;
import oleborn.analyticsservice.service.OrderLifecycleService;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@KafkaListener(topics = "${app.topic.payment-events}", groupId = "analytics-service-group")
public class PaymentEventConsumer {

    private final OrderLifecycleService service;

    @KafkaHandler
    public void onPaymentStarted(PaymentStartedEvent event, Acknowledgment ack) {
        try {

            log.info("Received PaymentStarted: orderId={}, timestamp={}", event.orderId(), event.timestamp());

            service.upsertPaymentStartedAt(event.orderId(), event.timestamp());

            ack.acknowledge();

            log.debug("PaymentStarted processed for order {}", event.orderId());
        } catch (Exception e) {
            log.error("Error processing PaymentStarted for order {}", event.orderId(), e);
            throw e;
        }
    }

    @KafkaHandler
    public void onPaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        try {

            log.info("Received PaymentCompleted: orderId={}, timestamp={}, status={}",
                    event.orderId(), event.timestamp(), event.status());

            service.upsertPaymentCompletedAt(event.orderId(), event.timestamp(), event.status());

            ack.acknowledge();

            log.debug("PaymentCompleted processed for order {}", event.orderId());
        } catch (Exception e) {
            log.error("Error processing PaymentCompleted for order {}", event.orderId(), e);
            throw e;
        }
    }

    @KafkaHandler(isDefault = true)
    public void handleUnknown(Object unknown, Acknowledgment ack) {
        log.warn("Unknown event type in payment-events: {}", unknown.getClass().getName());
        ack.acknowledge(); // или не подтверждать, если хотите разобраться
    }
}