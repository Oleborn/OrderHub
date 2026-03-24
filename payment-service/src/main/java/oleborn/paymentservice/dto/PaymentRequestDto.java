package oleborn.paymentservice.dto;

import java.math.BigDecimal;

public record PaymentRequestDto(

        Long orderId,
        BigDecimal amount

) {
}
