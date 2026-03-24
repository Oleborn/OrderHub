package oleborn.order_service.order.domain.dto;

public record NotificationRequest(
        Long orderId,
        String eventType
) {}