package oleborn.notificationservice.service;

import oleborn.notificationservice.event.NotificationEvent;

public interface NotificationService {

    void sendNotification(NotificationEvent event);

}
