package oleborn.paymentservice.controller;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oleborn.paymentservice.dto.PaymentRequestDto;
import oleborn.paymentservice.dto.PaymentResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
public class PaymentController {

    private final AtomicBoolean failureMode = new AtomicBoolean(false);
    private final Random random = new Random();

    @PostMapping("/process")
    @SneakyThrows
    public ResponseEntity<PaymentResponseDto> processPayment(@RequestBody PaymentRequestDto request) {

        log.info("Обработка оплаты для заказ id: {}, сумма: {}", request.orderId(), request.amount());

        if (failureMode.get()) {

            int randomRequest = random.nextInt(100);

            // Имитация ошибки
            if (randomRequest < 80) {

                log.error("Симуляция ошибки оплаты заказа id: {}", request.orderId());

                //типа отловили ошибку и вернули читаемый статус
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(
                                new PaymentResponseDto(false, "Сервис оплаты недоступен")
                        );
            }

        }
        return ResponseEntity.ok(new PaymentResponseDto(true, "Оплата проведена"));
    }

    @GetMapping("/admin/failure-mode")
    public void setFailureMode(@RequestParam boolean enabled) {

        failureMode.set(enabled);

        log.info("Failure mode в Payment Service, переключен на: {}", enabled);
    }
}