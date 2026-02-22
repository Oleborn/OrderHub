package oleborn.orderhub_project.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.orderhub_project.order.client.NotificationClient;
import oleborn.orderhub_project.order.domain.dto.OrderCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final NotificationClient notificationClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {

        log.debug("Получено событие о создании заказа: {}, дата: {}", event.orderId(), event.timestamp());

        try {
            notificationClient.notifyOrderCreated(event.orderId());
            log.debug("Уведомление отправлено для заказа: {}", event.orderId());
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления для заказа: {}", event.orderId(), e);
        }
    }
}
