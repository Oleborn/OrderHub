package oleborn.notificationservice;

public record NotificationRequest(
        long orderId,
        String eventType
) {
}
