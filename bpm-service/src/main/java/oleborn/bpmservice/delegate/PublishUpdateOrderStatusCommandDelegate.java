package oleborn.bpmservice.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.bpmservice.domain.comand.UpdateOrderStatusCommand;
import oleborn.bpmservice.messaging.producer.OrderProducer;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component("publishUpdateOrderStatusCommandDelegate")
@Slf4j
@RequiredArgsConstructor
public class PublishUpdateOrderStatusCommandDelegate implements JavaDelegate {

    private final OrderProducer orderProducer;

    @Override
    public void execute(DelegateExecution execution) {

        Long orderId = (Long) execution.getVariable("orderId");
        String transactionId = (String) execution.getVariable("transactionId");
        String traceparent = (String) execution.getVariable("traceparent");

        log.info("traceparent!!! {}", traceparent);

        log.info("Publishing UpdateOrderStatusCommand (PAID) for order: {}", orderId);

        UpdateOrderStatusCommand command = UpdateOrderStatusCommand.builder()
                .orderId(orderId)
                .transactionId(transactionId)
                .newStatus("PAID")
                .build();

        try {
            orderProducer.sendUpdateCommand(command,  traceparent);

        } catch (Exception e) {
            log.error("Failed to send UpdateOrderStatusCommand for order: {}", orderId, e);
            throw new RuntimeException("Failed to send UpdateOrderStatusCommand for order " + orderId, e);
        }
    }
}