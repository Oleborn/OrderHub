package oleborn.paymentservice.service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.paymentservice.domain.event.OrderCreatedEvent;
import oleborn.paymentservice.service.PaymentService;
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
    private final PaymentService paymentService;

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
            topics = "${app.topic.order-create-topic}", // берём из application.yaml
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(
            ConsumerRecord<String, byte[]> record,
            Acknowledgment acknowledgment
    ) {
        log.info("Принято сообщение из топика order.outbox");

        try {
            // 1. Десериализуем событие
            byte[] value = record.value();
            OrderCreatedEvent event = objectMapper.readValue(value, OrderCreatedEvent.class);
            Long orderId = event.orderId();

            // 2. Бизнес-валидация
            if (orderId == null) {
                throw new IllegalArgumentException("orderId must not be null");
            }

            log.info("Обработка оплаты для заказа {}", orderId);

            // 3. Выполняем оплату (имитация бизнес-логики)
            paymentService.processPayment(event);

            // 4. Подтверждаем offset
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Ошибка обработки события оплаты", e);
            // Не подтверждаем offset – после retry попадёт в DLT
            throw new RuntimeException("Payment processing failed", e);
        }
    }
}