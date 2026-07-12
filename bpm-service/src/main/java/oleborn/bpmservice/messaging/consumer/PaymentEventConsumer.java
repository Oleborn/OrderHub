package oleborn.bpmservice.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.bpmservice.domain.event.PaymentCompletedEvent;
import oleborn.bpmservice.domain.event.PaymentFailedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final RuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.topic.payment-events}", groupId = "workflow-group")
    public void handlePaymentEvent(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        try {
            String json = new String(record.value());
            log.debug("Received payment event: {}", json);

            // Определяем тип события по наличию поля transactionId
            if (json.contains("\"transactionId\"")) {
                handlePaymentCompleted(json);
            } else {
                handlePaymentFailed(json);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process payment event", e);
            // При ошибке не коммитим offset – сообщение будет обработано повторно
        }
    }

    private void handlePaymentCompleted(String json) throws IOException {
        PaymentCompletedEvent event = objectMapper.readValue(json, PaymentCompletedEvent.class);
        log.info("Received PaymentCompletedEvent for order: {}", event.orderId());

        // Коррелируем сообщение с процессом
        runtimeService.createMessageCorrelation("paymentCompleted")
                .processInstanceVariableEquals("orderId", event.orderId())
                .setVariable("transactionId", event.transactionId())
                .correlateWithResult();
    }

    private void handlePaymentFailed(String json) throws IOException {
        PaymentFailedEvent event = objectMapper.readValue(json, PaymentFailedEvent.class);
        log.info("Received PaymentFailedEvent for order: {}", event.orderId());

        // Коррелируем сообщение с процессом
        runtimeService.createMessageCorrelation("paymentFailed")
                .processInstanceVariableEquals("orderId", event.orderId())
                .setVariable("failureReason", event.reason())
                .correlateWithResult();
    }
}