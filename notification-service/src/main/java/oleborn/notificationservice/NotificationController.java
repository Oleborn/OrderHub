package oleborn.notificationservice;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Slf4j
public class NotificationController {

    @PostMapping
    @SneakyThrows
    public ResponseEntity<Void> notify(@RequestBody NotificationRequest request) {
        log.info("Отправка сообщения по номеру заказа {}, типа: {}", request.orderId(), request.eventType());

        Thread.sleep(5000);

        return ResponseEntity.ok().build();
    }
}