package oleborn.analyticsservice.domain.dto;

public record ProcessingMetricsDto(
        Double avgPaymentProcessingTimeSec,
        Double avgNotificationTimeSec
) {}