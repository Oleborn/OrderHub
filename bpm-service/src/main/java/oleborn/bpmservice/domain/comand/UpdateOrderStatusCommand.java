package oleborn.bpmservice.domain.comand;

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