package oleborn.bpmservice.messaging.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.bpmservice.domain.comand.CancelOrderCommand;
import oleborn.bpmservice.domain.comand.ProcessPaymentCommand;
import oleborn.bpmservice.domain.comand.UpdateOrderStatusCommand;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProducer {

    @Value("${app.topic.order-commands}")
    private String orderCommandsTopic;

    private final KafkaTemplate<String, Object> reliableKafkaTemplate;

    public void sendUpdateCommand(UpdateOrderStatusCommand command, String traceparent) {
        send(orderCommandsTopic, String.valueOf(command.orderId()), command, traceparent);
    }

    public void sendCancelCommand(CancelOrderCommand command, String traceparent) {
        send(orderCommandsTopic, String.valueOf(command.orderId()), command,  traceparent);
    }

    private void send(String topic, String key, Object event, String traceparent) {

        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);

        if (traceparent != null) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        //Возвращает CompletableFuture, который завершится, когда брокер подтвердит получение (или будет ошибка)
        CompletableFuture<SendResult<String, Object>> future = reliableKafkaTemplate.send(record);

        //Асинхронное ожидание результата. Позволяет выполнить код после того, как брокер ответит (или ошибка), не блокируя основной поток
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Событие отправлено в topic: {}, partition: {}, offset: {}",
                        orderCommandsTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            } else {
                log.error("Ошибка отправки события по orderId: {}", key, ex);
                // Здесь можно сохранить в outbox
            }
        });
    }

}
