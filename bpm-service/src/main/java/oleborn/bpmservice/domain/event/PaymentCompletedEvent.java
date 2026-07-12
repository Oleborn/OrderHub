package oleborn.bpmservice.domain.event;

public record PaymentCompletedEvent(
        Long orderId,
        String transactionId,
        String status
) {
}