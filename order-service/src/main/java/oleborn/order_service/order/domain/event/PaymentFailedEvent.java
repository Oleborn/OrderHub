package oleborn.order_service.order.domain.event;

public record PaymentFailedEvent(
        Long orderId,
        String reason
) {}