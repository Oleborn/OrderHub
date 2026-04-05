package oleborn.order_service.order.feignclient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.PaymentRequestDto;
import oleborn.order_service.order.domain.dto.PaymentResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/*
Использовать для нативного ограничения количества занятых потоков (Bulkhead для FeignClient)
 */
@Component
@Slf4j
public class PaymentFeignClientWrapper {

    private final Semaphore semaphore;
    private final PaymentFeignClient client;

    public PaymentFeignClientWrapper(
            @Value("${bulkhead.countThreads:5}") int countThreads,
            PaymentFeignClient client
    ) {
        this.semaphore = new Semaphore(countThreads);
        this.client = client;
    }

    public PaymentResponseDto processPayment(PaymentRequestDto request) {
        if (!semaphore.tryAcquire()) {
            log.warn("Bulkhead full, rejecting call");
            throw new RuntimeException("Service busy");
        }
        try {
            return client.processPayment(request);
        } finally {
            semaphore.release();
        }
    }
}