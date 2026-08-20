package oleborn.analyticsservice.domain.event;

import java.time.Instant;

public record PaymentCompletedEvent(
        Long orderId,
        String transactionId,
        String status,
        Instant timestamp
) {}