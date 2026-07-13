package oleborn.bpmservice.domain.comand;

import lombok.Builder;

@Builder
public record UpdateOrderStatusCommand (
    Long orderId,
    String transactionId,
    String newStatus
) {}