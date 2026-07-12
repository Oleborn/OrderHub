package oleborn.order_service.order.domain.command;

import lombok.Builder;

@Builder
public record CancelOrderCommand(
        Long orderId,
        String reason
) {
}
