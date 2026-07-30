package oleborn.order_service.order.domain.command;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateOrderStatusCommand(
        UUID commandId,
        Long orderId,
        String transactionId,
        String newStatus
) {
}