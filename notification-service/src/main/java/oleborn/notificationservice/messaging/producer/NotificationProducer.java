package oleborn.notificationservice.messaging.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.notificationservice.event.NotificationSentEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationProducer {

    @Value("${app.topic.notification-events}")
    private String notificationEventsTopic;

    private final KafkaTemplate<String, Object> reliableKafkaTemplate;

    public void sendPaymentCompletedEvent(NotificationSentEvent event) {
        send(notificationEventsTopic, String.valueOf(event.orderId()), event);
    }

    private void send(String topic, String key, Object event) {

        //Возвращает CompletableFuture, который завершится, когда брокер подтвердит получение (или будет ошибка)
        CompletableFuture<SendResult<String, Object>> future = reliableKafkaTemplate.send(topic, key, event);

        //Асинхронное ожидание результата. Позволяет выполнить код после того, как брокер ответит (или ошибка), не блокируя основной поток
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Событие отправлено в topic: {}, partition: {}, offset: {}",
                        notificationEventsTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            } else {
                log.error("Ошибка отправки события по orderId: {}", key, ex);
            }
        });
    }
}
