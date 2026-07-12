package oleborn.bpmservice.domain.comand;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ProcessPaymentCommand(
        Long orderId,
        BigDecimal amount
) {
}