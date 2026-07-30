package oleborn.order_service.order.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.command.CancelOrderCommand;
import oleborn.order_service.order.domain.command.UpdateOrderStatusCommand;
import oleborn.order_service.order.service.OrderService;
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
        topics = "${app.topic.order-commands}",
        groupId = "order-service-group",
        containerFactory = "kafkaListenerContainerFactory"
)
public class BpmConsumer {

    private final OrderService orderService;

    @KafkaHandler
    public void handleBpmCommand(
            UpdateOrderStatusCommand updateCommand,
            Acknowledgment acknowledgment
    ) {
        log.info("Принято сообщение из топика order-commands UpdateOrderStatusCommand: {}", updateCommand);

        orderService.completeOrder(updateCommand);

        acknowledgment.acknowledge();

    }

    @KafkaHandler
    public void handleBpmCommand(
            CancelOrderCommand failedCommand,
            Acknowledgment acknowledgment
    ) {
        log.info("Принято сообщение из топика order-commands CancelOrderCommand: {}", failedCommand);

        orderService.cancelOrder(failedCommand);

        acknowledgment.acknowledge();
    }

    @KafkaHandler(isDefault = true)
    public void handleUnknown(Object command, Acknowledgment acknowledgment) {
        log.warn("Неизвестный тип команды из order-commands: {}", command.getClass());
        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDlt(Object command, Acknowledgment acknowledgment) {
        log.error("Команда ушла в DLT после исчерпания попыток: {}", command);
        acknowledgment.acknowledge();
    }
}