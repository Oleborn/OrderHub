package oleborn.order_service.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.dictionary.OutboxStatus;
import oleborn.order_service.order.repository.OutboxEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxStatusUpdater {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${debezium.connector.outbox-route-topic}",
        groupId = "order-service-status-updater",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void updateStatus(ConsumerRecord<String, String> record) {
        try {
            // Из payload достаём id события
            JsonNode json = objectMapper.readTree(record.value());
            Long eventId = json.get("orderId").asLong();
            
            // Обновляем статус или удаляем запись
            outboxRepository.updateStatus(eventId, OutboxStatus.PUBLISHED);

            log.info("Обновлен outbox статус события {} на PUBLISHED", eventId);

        } catch (Exception e) {
            log.error("Failed to update outbox status", e);
        }
    }
}