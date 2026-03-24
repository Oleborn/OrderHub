package oleborn.notificationservice;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/notifications")
@Slf4j
public class NotificationController {

    private final AtomicBoolean failureMode = new AtomicBoolean(false);
    private final Random random = new Random();

    @PostMapping
    @SneakyThrows
    public ResponseEntity<Void> notify(@RequestBody NotificationRequest request) {

        if (failureMode.get()) {

            int randomInt = random.nextInt(100);

            log.info("Выпало число: {}", randomInt);

            if (randomInt < 30) {
                log.error("Возникли проблемы с отправкой информации по заказу: {}", request.orderId());
                throw new RuntimeException("Типа проблемы с отправкой уведомления");
            }

            if (randomInt > 70) {
                log.warn("NotificationService замедлился");
                Thread.sleep(200);
            }
        }

        log.info("Сообщение по номеру заказа {}, типа: {}, успешно отправлено!", request.orderId(), request.eventType());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/failure-mode")
    public void setFailureMode(@RequestParam boolean enabled) {

        failureMode.set(enabled);

        log.info("Failure mode в Notification Service, переключен на: {}", enabled);
    }
}