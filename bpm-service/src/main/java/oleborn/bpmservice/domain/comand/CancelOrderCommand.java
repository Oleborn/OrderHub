package oleborn.bpmservice.domain.comand;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CancelOrderCommand(
        UUID commandId,
        Long orderId,
        String reason
) {
}
