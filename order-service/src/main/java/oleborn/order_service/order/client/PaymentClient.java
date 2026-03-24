package oleborn.order_service.order.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.PaymentRequestDto;
import oleborn.order_service.order.domain.dto.PaymentResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentClient {

    private final WebClient paymentWebClient;

    public Mono<PaymentResponseDto> processPayment(Long orderId, BigDecimal amount) {

        return paymentWebClient.post()
                .uri("/api/v1/payments/process")
                .bodyValue(new PaymentRequestDto(orderId, amount))
                .retrieve()
                .bodyToMono(PaymentResponseDto.class);
    }
}