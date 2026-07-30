package oleborn.paymentservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
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
import org.springframework.kafka.support.serializer.JsonSerializer;
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
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-service-group");

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
                processPayment:oleborn.paymentservice.domain.command.ProcessPaymentCommand
                """);

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "oleborn.paymentservice.domain");

        // При необходимости здесь можно добавить дополнительные свойства:
        // - MAX_POLL_RECORDS_CONFIG (количество записей за один вызов poll)
        // - MAX_POLL_INTERVAL_MS_CONFIG (максимальный интервал между poll)
        // - SESSION_TIMEOUT_MS_CONFIG и другие

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // Ручное подтверждение: сообщение должно быть подтверждено через Acknowledgment.acknowledge()
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Количество потребительских потоков (concurrency). Если топик имеет 3 партиции,
        // можно установить concurrency = 3 для параллельной обработки.
        factory.setConcurrency(1); // для демо достаточно одного потока

        // Здесь также можно задать обработчик ошибок (DefaultErrorHandler),
        // фильтры и другие параметры.


        // Включаем observability через ContainerProperties
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }

    @Bean
    public SmartMessageConverter smartMessageConverter(ObjectMapper objectMapper) {
        MappingJacksonParameterizedConverter converter = new MappingJacksonParameterizedConverter();
        converter.setObjectMapper(objectMapper);
        // При желании можно настроить converter.setTypePrecedence(), converter.setStrictContentTypeMatch() и т.д.
        return converter;
    }
}