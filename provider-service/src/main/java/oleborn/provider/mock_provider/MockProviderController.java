package oleborn.provider.mock_provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;


@RestController
@RequestMapping("/api/mock-provider")
@Slf4j
public class MockProviderController {

    @PostMapping
    public Mono<ResponseEntity<ProviderResponse>> sendNotification(
            @RequestBody OrderCreatedEvent event
    ) {

        log.info("Мок провайдер принял в обработку запрос на уведомление по заказу: {}", event.orderId());

        // --- 1. Генерируем случайные параметры ---
        int dice = ThreadLocalRandom.current().nextInt(100);      // число от 0 до 99

        long delayMs = ThreadLocalRandom.current().nextLong(100, 500); // задержка от 100 до 500 мс

        // --- 2. Имитация задержки ответа (эмуляция обработки внешним API) ---
        return Mono.delay(Duration.ofMillis(delayMs))
                .flatMap(ignored -> {
                    // --- 3. Логика поведения в зависимости от случайного числа ---

                    if (dice < 5) {
                        // 5% — ошибка 429 Too Many Requests (имитация rate limit провайдера)
                        log.warn("Мок провайдер вернул 429 статус по заказу {}", event.orderId());
                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
                    } else if (dice < 10) {
                        // 5% — ошибка 500 Internal Server Error (имитация сбоя провайдера)
                        log.warn("Мок провайдер вернул 500 статус по заказу {}", event.orderId());
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                    } else if (dice < 20) {
                        // 10% — HTTP 200, но success=false (провайдер отклонил уведомление)
                        log.warn("Мок провайдер вернул success=false по заказу {}", event.orderId());
                        ProviderResponse response = new ProviderResponse(false, "Provider declined notification");
                        return Mono.just(ResponseEntity.ok(response));
                    } else {
                        // 80% — успешный ответ (HTTP 200, success=true)
                        log.info("Мок провайдер успешно обработал отпраку уведомления по заказу {}", event.orderId());
                        ProviderResponse response = new ProviderResponse(true, "Notification sent successfully");
                        return Mono.just(ResponseEntity.ok(response));
                    }
                });
    }
}