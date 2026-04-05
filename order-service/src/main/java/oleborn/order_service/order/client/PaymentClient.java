package oleborn.order_service.order.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.PaymentRequestDto;
import oleborn.order_service.order.domain.dto.PaymentResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.concurrent.Semaphore;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentClient {

    private final WebClient paymentWebClient;
    private final Semaphore semaphore = new Semaphore(5);

    //использовать для нативного Bulkhead через Semaphore
    public PaymentResponseDto processPaymentBulkhead(Long orderId, BigDecimal amount) {

        if (!semaphore.tryAcquire()) {
            log.warn("Bulkhead full");
            throw new RuntimeException("Payment service busy");
        }

        try {
            return paymentWebClient.post()
                    .uri("/api/v1/payments/process")
                    .bodyValue(new PaymentRequestDto(orderId, amount))
                    .retrieve()
                    .bodyToMono(PaymentResponseDto.class)
                    .block();
        } finally {
            semaphore.release();
        }
    }


    public Mono<PaymentResponseDto> processPayment(Long orderId, BigDecimal amount) {

        return paymentWebClient.post()
                .uri("/api/v1/payments/process")
                .bodyValue(new PaymentRequestDto(orderId, amount))
                .retrieve()
                .bodyToMono(PaymentResponseDto.class);
    }
}