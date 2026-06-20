package oleborn.paymentservice.domain.dto;

public record PaymentResponseDto(

        Boolean isSuccessful,
        String message

) {
}
