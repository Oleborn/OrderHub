package oleborn.paymentservice.domain.event;

public record PaymentFailedEvent(
        Long orderId,
        String reason
) {}