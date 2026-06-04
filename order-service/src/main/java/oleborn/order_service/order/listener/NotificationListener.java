package oleborn.order_service.order.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.client.NotificationClient;
import oleborn.order_service.order.domain.dto.OrderCreatedEvent;
import oleborn.order_service.order.producer.KafkaNotificationProducer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class NotificationListener {

    private final KafkaNotificationProducer kafkaNotificationProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {

        log.debug("Получено событие о создании заказа: {}, дата: {}", event.orderId(), event.timestamp());

        try {

            kafkaNotificationProducer.sendOrderCreatedEvent(event);

            log.debug("Уведомление отправлено для заказа: {}", event.orderId());
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления для заказа: {}", event.orderId(), e);
            throw new RuntimeException("Ошибка передачи данных в кафка");
        }
    }
}
