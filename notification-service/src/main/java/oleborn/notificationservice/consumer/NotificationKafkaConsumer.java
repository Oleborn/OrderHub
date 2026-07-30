package oleborn.notificationservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.notificationservice.event.NotificationEvent;
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
        concurrency = "3"
)
@KafkaListener(
        topics = "${app.topic.notification-events}",
        groupId = "notification-service-group",
        containerFactory = "kafkaListenerContainerFactory"
)
public class NotificationKafkaConsumer {

    @KafkaHandler
    public void consumeResultProcessOrder(NotificationEvent event, Acknowledgment acknowledgment) {

        try {
            // Бизнес-валидация
            if (event.orderId() == null) {
                throw new IllegalArgumentException("orderId must not be null");
            }

            if (event.transactionId() != null) {
                log.info(
                        """
                         Отправлено уведомление:
                         Заказ: {}, успешно обработан.
                         Статус заказа: {}
                         Id транзакции: {}
                         """,
                        event.orderId(),
                        event.status(),
                        event.transactionId()
                );
            } else {
                log.info(
                        """
                         Отправлено уведомление:
                         Заказ: {}, не обработан.
                         Статус заказа: {}
                         Причина: {}
                         """,
                        event.orderId(),
                        event.status(),
                        event.reason()
                );
            }

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