package oleborn.analyticsservice.domain.event;

import java.time.Instant;

public record PaymentStartedEvent(
        Long orderId,
        Instant timestamp
) {}