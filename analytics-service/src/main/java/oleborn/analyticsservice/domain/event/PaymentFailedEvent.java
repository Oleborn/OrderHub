package oleborn.analyticsservice.domain.event;

import java.time.Instant;

public record PaymentFailedEvent(
        Long orderId,
        String reason,
        Instant timestamp
) {}