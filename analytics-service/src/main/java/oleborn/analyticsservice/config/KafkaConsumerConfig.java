package oleborn.analyticsservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.converter.MappingJacksonParameterizedConverter;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.messaging.converter.SmartMessageConverter;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        // Адреса брокеров Kafka (из YAML)
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Идентификатор consumer group (обязателен для группового управления offset)
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // С какой позиции начинать чтение, если offset не задан: earliest — с самого начала
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Отключаем авто-коммит offset — будем подтверждать вручную после успешной обработки
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);

        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        props.put(JsonDeserializer.TYPE_MAPPINGS,
                """
                OrderCreatedEvent:oleborn.analyticsservice.domain.event.OrderCreatedEvent,
                paymentStartedEvent:oleborn.analyticsservice.domain.event.PaymentStartedEvent,
                paymentCompletedEvent:oleborn.analyticsservice.domain.event.PaymentCompletedEvent,
                notificationSentEvent:oleborn.analyticsservice.domain.event.NotificationSentEvent,
                paymentFailedEvent:oleborn.analyticsservice.domain.event.PaymentFailedEvent
                """);

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "oleborn.analyticsservice.domain");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(1);
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }

    @Bean
    public SmartMessageConverter smartMessageConverter(ObjectMapper objectMapper) {
        MappingJacksonParameterizedConverter converter = new MappingJacksonParameterizedConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }
}