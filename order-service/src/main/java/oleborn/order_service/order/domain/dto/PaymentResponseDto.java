package oleborn.order_service.order.domain.dto;

public record PaymentResponseDto(

        Boolean isSuccessful,
        String message

) {
}
