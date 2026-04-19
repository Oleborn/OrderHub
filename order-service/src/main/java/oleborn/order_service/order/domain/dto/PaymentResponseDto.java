package oleborn.order_service.order.domain.dto;

import lombok.Builder;

@Builder
public record PaymentResponseDto(

        Boolean isSuccessful,
        String message,
        boolean requiresPendingProcessing

) {
}
