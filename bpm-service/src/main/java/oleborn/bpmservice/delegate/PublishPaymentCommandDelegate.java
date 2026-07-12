package oleborn.bpmservice.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.bpmservice.domain.comand.ProcessPaymentCommand;
import oleborn.bpmservice.messaging.producer.PaymentProducer;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component("publishPaymentCommandDelegate")
@Slf4j
@RequiredArgsConstructor
public class PublishPaymentCommandDelegate implements JavaDelegate {

    private final PaymentProducer paymentProducer;

    @Override
    public void execute(DelegateExecution execution) {

        Long orderId = (Long) execution.getVariable("orderId");
        BigDecimal totalAmount = (BigDecimal) execution.getVariable("totalAmount");
        String traceparent = (String) execution.getVariable("traceparent");

        log.info("Publishing ProcessPaymentCommand for order: {}", orderId);

        ProcessPaymentCommand command = ProcessPaymentCommand.builder()
                .orderId(orderId)
                .amount(totalAmount)
                .build();

        try {
            paymentProducer.sendCommand(command, traceparent);

        } catch (Exception e) {
            log.error("Failed to send ProcessPaymentCommand for order: {}", orderId, e);

            //исключение, чтобы Camunda активировала ретраи
            throw new RuntimeException("Failed to send ProcessPaymentCommand for order " + orderId, e);
        }
    }
}