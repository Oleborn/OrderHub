package oleborn.analyticsservice.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.analyticsservice.domain.event.OrderCreatedEvent;
import oleborn.analyticsservice.service.OrderLifecycleService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final OrderLifecycleService service;

    @KafkaListener(topics = "${app.topic.order-create-topic}", groupId = "analytics-service-group")
    public void onOrderCreated(OrderCreatedEvent event, Acknowledgment ack) {

        try {
            log.info("Received OrderCreated: orderId={}, timestamp={}", event.orderId(), event.timestamp());

            // Конвертируем LocalDateTime → Instant (UTC)
            service.upsertCreatedAt(event.orderId(), event.timestamp().toInstant(ZoneOffset.UTC));

            ack.acknowledge();

            log.debug("OrderCreated processed for order {}", event.orderId());

        } catch (Exception e) {
            log.error("Error processing OrderCreated for order {}", event.orderId(), e);
            throw e; // пойдёт в ретрай / DLQ
        }
    }
}