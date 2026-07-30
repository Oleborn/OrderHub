package oleborn.bpmservice.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.bpmservice.domain.event.OrderCreatedEvent;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final RuntimeService runtimeService;

    @KafkaListener(topics = "${app.topic.order-create-topic}", groupId = "workflow-group")
    public void handleOrderCreated(
            OrderCreatedEvent event,
            @Header(name = "traceparent", required = false) String traceparent,
            Acknowledgment acknowledgment
    ) {
        try {

            log.info("Received payment event: {}", event);

            log.info("Received OrderCreatedEvent for order: {}", event.orderId());

            // Запускаем BPMN-процесс
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderId", event.orderId());
            variables.put("timestamp", event.timestamp());
            variables.put("context", event.context());
            variables.put("timeoutDuration", "PT30S");
            variables.put("traceparent", traceparent);

            runtimeService.startProcessInstanceByKey("create-order-saga", variables);

            log.info("BPMN process started for order: {}", event.orderId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to start workflow for order", e);
        }
    }
}