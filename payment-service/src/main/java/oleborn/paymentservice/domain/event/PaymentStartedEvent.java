package oleborn.paymentservice.domain.event;

import java.time.Instant;

public record PaymentStartedEvent(
        Long orderId,
        Instant timestamp
) {}