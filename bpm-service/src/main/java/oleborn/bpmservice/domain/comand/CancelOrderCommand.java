package oleborn.bpmservice.domain.comand;

import lombok.Builder;

@Builder
public record CancelOrderCommand(
        Long orderId,
        String reason
) {
}
