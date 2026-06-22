package oleborn.order_service.order.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.event.PaymentCompletedEvent;
import oleborn.order_service.order.domain.event.PaymentFailedEvent;
import oleborn.order_service.order.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
public class PaymentConsumer {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

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
            concurrency = "3",
            autoStartDltHandler = "true"
    )

    @KafkaListener(
            topics = "${app.topic.payment-events}",
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )

    public void handlePaymentEvent(
            ConsumerRecord<String, byte[]> record,
            Acknowledgment acknowledgment
    ) {
        log.info("Принято сообщение из топика payment-events");

        try {
            byte[] value = record.value();
            // Определяем тип события по заголовку или по содержимому
            // Для простоты будем пробовать десериализовать сначала как PaymentCompletedEvent,
            // если не получится – пробуем как PaymentFailedEvent.
            // Но лучше использовать заголовок __TypeId__, который добавляет JsonSerializer.
            // В нашем случае мы можем положить тип в отдельное поле, или использовать заголовки.
            // Для демонстрации я покажу упрощённый вариант:

            String json = new String(value);

            if (json.contains("\"transactionId\"")) {

                PaymentCompletedEvent completed = objectMapper.readValue(value, PaymentCompletedEvent.class);
                orderService.completeOrder(completed.orderId());

            } else if (json.contains("\"reason\"")) {

                PaymentFailedEvent failed = objectMapper.readValue(value, PaymentFailedEvent.class);
                orderService.cancelOrder(failed.orderId(), failed.reason());

            } else {
                throw new IllegalArgumentException("Unknown event type");
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Ошибка обработки события оплаты", e);
            throw new RuntimeException("Payment event processing failed", e);
        }
    }


}