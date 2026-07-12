package oleborn.bpmservice.domain.event;

public record PaymentFailedEvent(
        Long orderId,
        String reason
) {}