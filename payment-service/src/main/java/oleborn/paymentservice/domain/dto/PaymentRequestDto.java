package oleborn.paymentservice.domain.dto;

import java.math.BigDecimal;

public record PaymentRequestDto(

        Long orderId,
        BigDecimal amount

) {
}
