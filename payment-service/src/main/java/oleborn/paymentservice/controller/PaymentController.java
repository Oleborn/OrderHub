package oleborn.paymentservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oleborn.paymentservice.domain.dto.PaymentRequestDto;
import oleborn.paymentservice.domain.dto.PaymentResponseDto;
import oleborn.paymentservice.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final AtomicBoolean failureMode = new AtomicBoolean(false);
    private final Random random = new Random();
    private final PaymentService paymentService;

    @PostMapping("/process")
    @SneakyThrows
    public ResponseEntity<PaymentResponseDto> processPayment(@RequestBody PaymentRequestDto request) {

        log.info("Обработка оплаты для заказ id: {}, сумма: {}", request.orderId(), request.amount());

        if (failureMode.get()) {

            int randomRequest = random.nextInt(100);

            // Имитация ошибки
            if (randomRequest < 60) {

//                log.error("Симуляция ошибки оплаты заказа id: {}", request.orderId());
//
//                //типа отловили ошибку и вернули читаемый статус
//                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
//                        .body(
//                                new PaymentResponseDto(false, "Сервис оплаты недоступен")
//                        );

                log.error("Симуляция замедления оплаты заказа id: {}", request.orderId());

                Thread.sleep(5000);
            }

        }
        return ResponseEntity.ok(new PaymentResponseDto(true, "Оплата проведена"));
    }

    @GetMapping("/admin/failure-mode")
    public void setFailureMode(@RequestParam boolean enabled) {
        paymentService.setFailureMode(enabled);
    }
}