package oleborn.analyticsservice.domain.dto;

import oleborn.analyticsservice.domain.entity.OrderLifecycle;

import java.time.Instant;

public record OrderTimelineDto(
        Long orderId,
        Instant createdAt,
        Instant paymentStartedAt,
        Instant paymentCompletedAt,
        String paymentStatus,
        Instant notificationSentAt
) {
    public static OrderTimelineDto from(OrderLifecycle record) {
        return new OrderTimelineDto(
                record.getOrderId(),
                record.getCreatedAt(),
                record.getPaymentStartedAt(),
                record.getPaymentCompletedAt(),
                record.getPaymentStatus(),
                record.getNotificationSentAt()
        );
    }
}