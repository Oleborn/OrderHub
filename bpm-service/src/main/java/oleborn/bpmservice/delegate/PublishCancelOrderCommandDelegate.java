package oleborn.bpmservice.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.bpmservice.domain.comand.CancelOrderCommand;
import oleborn.bpmservice.messaging.producer.OrderProducer;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component("publishCancelOrderCommandDelegate")
@Slf4j
@RequiredArgsConstructor
public class PublishCancelOrderCommandDelegate implements JavaDelegate {

    private final OrderProducer orderProducer;

    @Override
    public void execute(DelegateExecution execution) {

        Long orderId = (Long) execution.getVariable("orderId");
        String traceparent = (String) execution.getVariable("traceparent");

        log.info("traceparent!!! {}", traceparent);

        // Если есть причина отмены – можно получить из переменной, иначе дефолтная
        String reason = (String) execution.getVariable("failureReason");
        if (reason == null) {
            reason = "Payment timeout or failure";
        }

        log.info("Publishing CancelOrderCommand for order: {} reason: {}", orderId, reason);

        CancelOrderCommand command = CancelOrderCommand.builder()
                .orderId(orderId)
                .reason(reason)
                .build();

        try {
            orderProducer.sendCancelCommand(command,  traceparent);

        } catch (Exception e) {
            log.error("Failed to send CancelOrderCommand for order: {}", orderId, e);
            throw new RuntimeException("Failed to send CancelOrderCommand for order " + orderId, e);
        }
    }
}