package oleborn.order_service.order.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.event.NotificationEvent;
import oleborn.order_service.order.domain.event.OrderCreatedEvent;
import oleborn.order_service.order.messaging.producer.NotificationProducer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final NotificationProducer kafkaNotificationProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderUpdated(NotificationEvent event) {

        log.debug("Получено событие о результате заказа: {}, результат-статус: {}", event.orderId(), event.status());

        try {

            kafkaNotificationProducer.sendOrderUpdatedEvent(event);

            log.debug("Уведомление отправлено для заказа: {}", event.orderId());
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления для заказа: {}", event.orderId(), e);
            throw new RuntimeException("Ошибка передачи данных в кафка");
        }
    }
}
