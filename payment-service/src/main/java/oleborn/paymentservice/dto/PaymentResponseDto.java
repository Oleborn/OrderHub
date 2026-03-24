package oleborn.paymentservice.dto;

public record PaymentResponseDto(

        Boolean isSuccessful,
        String message

) {
}
