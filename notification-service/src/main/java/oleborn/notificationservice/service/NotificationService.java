package oleborn.notificationservice.service;

import oleborn.notificationservice.event.NotificationEvent;
import oleborn.notificationservice.event.OrderCreatedEvent;

public interface NotificationService {

    void sendNotification(NotificationEvent event);

    void sendOrderCreatedNotification(OrderCreatedEvent event);

}
