package oleborn.notificationservice.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.notificationservice.event.NotificationEvent;
import oleborn.notificationservice.service.NotificationService;
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
        kafkaTemplate = "reliableKafkaTemplate",
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
        concurrency = "3"
)
@KafkaListener(
        topics = "${app.topic.notification-events}",
        groupId = "notification-service-group",
        containerFactory = "kafkaListenerContainerFactory"
)
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;

    @KafkaHandler
    public void consumeResultProcessOrder(
            NotificationEvent event,
            Acknowledgment acknowledgment
    ) {

        try {

            notificationService.sendNotification(event);

            //Ручной коммит offset
            acknowledgment.acknowledge();
            log.info("Событие для заказа: {} обработано и оффсет для него сдвинут(acknowledged) ", event.orderId());

        } catch (Exception e) {
            log.error("Error processing order", e);
            // Не вызываем acknowledgment – сообщение попадёт в DLT после всех retry
            throw new RuntimeException("Processing failed", e);
        }
    }
}