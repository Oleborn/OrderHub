package oleborn.order_service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.dictionary.OutboxStatus;
import oleborn.order_service.order.domain.event.OrderCreatedEvent;
import oleborn.order_service.order.repository.OutboxEventRepository;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@KafkaListener(
        topics = "${debezium.connector.outbox-route-topic}",
        groupId = "order-service-status-updater",
        containerFactory = "kafkaListenerContainerFactory"
)
public class OutboxStatusUpdater {

    private final OutboxEventRepository outboxRepository;


    @Transactional
    @KafkaHandler
    public void updateStatus(OrderCreatedEvent event) {
        try {
            // Обновляем статус или удаляем запись
            outboxRepository.updateStatus(event.orderId(), OutboxStatus.PUBLISHED);

            log.info("Обновлен outbox статус события {} на PUBLISHED", event.orderId());

        } catch (Exception e) {
            log.error("Failed to update outbox status", e);
        }
    }
}