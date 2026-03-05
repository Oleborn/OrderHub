package oleborn.notificationservice;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequestMapping("/api/notifications")
@Slf4j
public class NotificationController {

    @PostMapping
    @SneakyThrows
    public ResponseEntity<Void> notify(@RequestBody NotificationRequest request) {
        log.info("Отправка сообщения по номеру заказа {}, типа: {}", request.orderId(), request.eventType());

        //TODO имитация проблемы, потом удалить
        int random = new Random().nextInt(100);

        log.info("Выпало число: {}", random);

        if (random < 10) {
            log.error("Возникли проблемы с отправкой уведомления по orderId: {}", request.orderId());
            throw new RuntimeException("Ну типа ошибка отправки сообщения");
        }

        if (random > 90) {
            log.info("Сервис notification service замедлился");
            Thread.sleep(600);
        }

        return ResponseEntity.ok().build();
    }
}