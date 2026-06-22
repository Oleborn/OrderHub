package oleborn.order_service.order.domain.event;

public record PaymentCompletedEvent(
        Long orderId,
        String transactionId,
        String status
) {}