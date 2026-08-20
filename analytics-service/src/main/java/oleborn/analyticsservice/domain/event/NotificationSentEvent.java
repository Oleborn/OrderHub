package oleborn.analyticsservice.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationSentEvent(
        Long orderId,
        String status,
        Instant timestamp
) {}