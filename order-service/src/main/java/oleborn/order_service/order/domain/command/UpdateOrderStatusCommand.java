package oleborn.order_service.order.domain.command;

import lombok.Builder;

@Builder
public record UpdateOrderStatusCommand (
    Long orderId,
    String transactionId,
    String newStatus
) {}