//package oleborn.order_service.outbox;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import oleborn.order_service.order.dictionary.OutboxStatus;
//import oleborn.order_service.order.domain.event.OutboxEvent;
//import oleborn.order_service.order.domain.event.OrderCreatedEvent;
//import oleborn.order_service.order.producer.KafkaNotificationProducer;
//import oleborn.order_service.order.repository.OutboxEventRepository;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.ZonedDateTime;
//import java.util.List;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class OutboxPoller {
//
//    private final OutboxEventRepository outboxEventRepository;
//    private final KafkaNotificationProducer kafkaNotificationProducer;
//    private final ObjectMapper objectMapper;
//
//    @Scheduled(fixedDelay = 5000)
//    @Transactional
//    public void poll() {
//
//        // 1. Забираем до 100 событий со статусом NEW (самые старые первыми)
//        List<OutboxEvent> events = outboxEventRepository.claimNewEvents(100);
//
//        if (events.isEmpty()) {
//            return;
//        }
//
//        log.info("Found {} events to publish", events.size());
//
//        // 2. Для каждого события
//        for (OutboxEvent event : events) {
//            try {
//                // 2.1 Десериализуем payload в OrderCreatedEvent
//                OrderCreatedEvent orderEvent = objectMapper.readValue(
//                    event.getPayload(),
//                    OrderCreatedEvent.class
//                );
//
//                // 2.2 Восстанавливаем контекст трассировки (traceId, spanId) – важно для observability!
//                // Но пока просто отправим – observability вернёмся позже
//
//                // 2.3 Отправляем в Kafka
//                kafkaNotificationProducer.sendOrderCreatedEvent(orderEvent);
//
//                // 2.4 Обновляем статус и время отправки
//                event.setStatus(OutboxStatus.PUBLISHED);
//                event.setProcessedAt(ZonedDateTime.now());
//                outboxEventRepository.save(event);
//
//                log.info("Event for order {} published to Kafka", orderEvent.orderId());
//            } catch (Exception e) {
//                log.error("Failed to publish event id={}", event.getId(), e);
//                // Статус не меняем – при следующем polling'е повторим
//            }
//        }
//    }
//}