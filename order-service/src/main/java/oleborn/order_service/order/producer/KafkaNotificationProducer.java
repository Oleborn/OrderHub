package oleborn.order_service.order.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.OrderCreatedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
@Deprecated
public class KafkaNotificationProducer {

    @Value("${app.topic.order-create-topic}")
    private String orderCreateTopic;

    // Используем надёжный продюсер (primary)
    private final KafkaTemplate<String, Object> reliableKafkaTemplate;

    // Если хотим показать транзакционный, инжектим квалификатор
    // private final KafkaTemplate<String, Object> transactionalKafkaTemplate;

    public void sendOrderCreatedEvent(OrderCreatedEvent event) {

//        //Все события для одного orderId будут попадать в одну партицию → сохраняется порядок событий по заказу
//        String key = String.valueOf(event.orderId());
//
////        throw new RuntimeException("Simulated crash before Kafka send");
//
//        //Возвращает CompletableFuture, который завершится, когда брокер подтвердит получение (или будет ошибка)
//        CompletableFuture<SendResult<String, Object>> future = reliableKafkaTemplate.send(orderCreateTopic, key, event);
//
//        //Асинхронное ожидание результата. Позволяет выполнить код после того, как брокер ответит (или ошибка), не блокируя основной поток
//        future.whenComplete((result, ex) -> {
//            if (ex == null) {
//                log.info("Событие отправлено в topic: {}, partition: {}, offset: {}",
//                        orderCreateTopic,
//                        result.getRecordMetadata().partition(),
//                        result.getRecordMetadata().offset()
//                );
//            } else {
//                log.error("Ошибка отправки события по orderId: {}", event.orderId(), ex);
//                // Здесь можно сохранить в outbox
//            }
//        });
    }
}