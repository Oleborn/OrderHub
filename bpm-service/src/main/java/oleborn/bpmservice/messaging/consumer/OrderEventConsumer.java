package oleborn.bpmservice.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.bpmservice.domain.event.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final RuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.topic.order-create-topic}", groupId = "workflow-group")
    public void handleOrderCreated(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        try {

            OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);

            log.info("Received OrderCreatedEvent for order: {}", event.orderId());

            // Запускаем BPMN-процесс
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderId", event.orderId());
            variables.put("timestamp",  event.timestamp());
            variables.put("context",  event.context());
            variables.put("timeoutDuration", "PT30S");
            variables.put("traceparent", getTraceparent(record));
            
            runtimeService.startProcessInstanceByKey("create-order-saga", variables);

            log.info("BPMN process started for order: {}", event.orderId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to start workflow for order", e);
        }
    }

    private String getTraceparent(ConsumerRecord<String, byte[]> record) {

        String traceparent = null;
        Headers headers = record.headers();
        if (headers != null) {
            Header header = headers.lastHeader("traceparent");
            if (header != null) {
                traceparent = new String(header.value(), StandardCharsets.UTF_8);
            }
        }

        log.info("Received trace parent for order: {}", traceparent);

        return traceparent;
    }
}