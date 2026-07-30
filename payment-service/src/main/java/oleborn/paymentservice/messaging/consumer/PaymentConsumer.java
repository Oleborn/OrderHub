package oleborn.paymentservice.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.paymentservice.domain.command.ProcessPaymentCommand;
import oleborn.paymentservice.service.PaymentService;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, maxDelay = 10000, multiplier = 2.0, random = true),
        timeout = "60000",
        retryTopicSuffix = "-retry",
        dltTopicSuffix = ".DLT",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
        exclude = {IllegalArgumentException.class, NullPointerException.class},
        traversingCauses = "true",
        dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR,
        autoCreateTopics = "true",
        numPartitions = "1",
        replicationFactor = "1",
        listenerContainerFactory = "kafkaListenerContainerFactory",
        kafkaTemplate = "reliableKafkaTemplate",
        concurrency = "3"
)

@KafkaListener(
        topics = "${app.topic.payment-commands}",
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory"
)
public class PaymentConsumer {

    private final PaymentService paymentService;

    @KafkaHandler
    public void handleOrderCreated(
            ProcessPaymentCommand command,
            Acknowledgment acknowledgment
    ) {
        log.info("Принято сообщение из топика payment-events");

        Long orderId = command.orderId();

        // 2. Бизнес-валидация
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        log.info("Обработка оплаты для заказа {}", orderId);

        // 3. Выполняем оплату (имитация бизнес-логики)
        paymentService.processPayment(command);

        // 4. Подтверждаем offset
        acknowledgment.acknowledge();
    }

    @KafkaHandler(isDefault = true)
    public void handleUnknown(Object command, Acknowledgment acknowledgment) {
        log.warn("Неизвестный тип команды из payment-commands: {}", command.getClass());
        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDlt(Object command, Acknowledgment acknowledgment) {
        log.error("Команда ушла в DLT после исчерпания попыток: {}", command);
        acknowledgment.acknowledge();
    }
}