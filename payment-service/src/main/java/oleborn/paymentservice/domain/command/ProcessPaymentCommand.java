package oleborn.paymentservice.domain.command;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ProcessPaymentCommand(
        Long orderId,
        BigDecimal amount
) {
}