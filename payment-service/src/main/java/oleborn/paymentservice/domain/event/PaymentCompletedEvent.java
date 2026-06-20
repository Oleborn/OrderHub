package oleborn.paymentservice.domain.event;

public record PaymentCompletedEvent(
        Long orderId,
        String transactionId,
        String status // например, "COMPLETED"
) {}