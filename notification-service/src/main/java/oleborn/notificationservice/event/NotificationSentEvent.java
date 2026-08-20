package oleborn.notificationservice.event;

import oleborn.notificationservice.dictionary.NotificationStatus;

import java.time.Instant;

public record NotificationSentEvent(
        Long orderId,
        NotificationStatus status,
        Instant timestamp
) {}