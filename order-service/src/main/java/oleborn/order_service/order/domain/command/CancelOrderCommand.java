package oleborn.order_service.order.domain.command;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CancelOrderCommand(
        UUID commandId,
        Long orderId,
        String reason
) {
}
