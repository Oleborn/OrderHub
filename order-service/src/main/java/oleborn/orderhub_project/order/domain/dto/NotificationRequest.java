package oleborn.orderhub_project.order.domain.dto;

public record NotificationRequest(
        Long orderId,
        String eventType
) {}