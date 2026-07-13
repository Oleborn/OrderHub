package oleborn.order_service.order.domain.event;

import lombok.Builder;

@Builder
public record NotificationEvent(
        Long orderId,
        String transactionId,
        String status,
        String reason
) {
}
