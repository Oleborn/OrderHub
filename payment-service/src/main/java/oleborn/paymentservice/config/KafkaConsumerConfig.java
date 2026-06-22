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
import org.springframework.messaging.converter.SmartMessageConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Consumer для payment-service.
 * <p>
 * ВНИМАНИЕ: Если настройка присутствует и в YAML, и в этом Java-конфиге,
 * то Java-конфигурация имеет более высокий приоритет и переопределяет YAML.
 * Это относится к таким параметрам, как bootstrap-servers, group.id, enable-auto-commit и т.д.
 * Однако, свойства, которые не переопределены здесь, будут взяты из YAML (или значений по умолчанию).
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Создаёт фабрику потребителей (ConsumerFactory) для работы с сырыми байтами.
     * <p>
     * Почему ByteArrayDeserializer? Потому что мы будем десериализовать JSON вручную
     * через ObjectMapper, что даёт полный контроль над процессом и не привязывает
     * сервис к общему DTO-модулю.
     *
     * @return ConsumerFactory для работы с ключами String и значениями byte[]
     */
    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        // Адреса брокеров Kafka (из YAML)
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Идентификатор consumer group (обязателен для группового управления offset)
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-service-group");

        // С какой позиции начинать чтение, если offset не задан: earliest — с самого начала
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Отключаем авто-коммит offset — будем подтверждать вручную после успешной обработки
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Десериализатор для ключа (обычно String, например, orderId)
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Десериализатор для значения — принимаем сырой массив байт
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        // При необходимости здесь можно добавить дополнительные свойства:
        // - MAX_POLL_RECORDS_CONFIG (количество записей за один вызов poll)
        // - MAX_POLL_INTERVAL_MS_CONFIG (максимальный интервал между poll)
        // - SESSION_TIMEOUT_MS_CONFIG и другие

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Фабрика контейнеров слушателей (ConcurrentKafkaListenerContainerFactory).
     * Настраивает ручной коммит (MANUAL_IMMEDIATE), чтобы подтверждать offset
     * только после того, как бизнес-логика выполнена успешно.
     *
     * @return фабрика, которую будет использовать @KafkaListener
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory = new ConcurrentKafkaListenerContainerFactory<>();
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

    /**
     * Конвертер сообщений для работы с параметризованными типами (generics).
     * <p>
     * Стандартный JsonDeserializer не умеет десериализовать, например, List<OrderCreatedEvent>
     * или Envelope<OrderCreatedEvent>, потому что информация о generic-типе стирается в runtime.
     * MappingJacksonParameterizedConverter решает эту проблему, сохраняя метаданные о типе.
     * <p>
     * Он необходим в следующих сценариях:
     * <ul>
     *   <li><b>Пакетная обработка (batch)</b>: если слушатель принимает List<OrderCreatedEvent>,
     *       без этого конвертера Spring не сможет превратить JSON-массив в список объектов.</li>
     *   <li><b>Сложные обёртки</b>: например, Envelope<T> или Result<T, Error>.</li>
     *   <li><b>Обобщённые обработчики</b>: когда один метод слушает несколько типов событий.</li>
     * </ul>
     * В нашем текущем сценарии мы используем ручную десериализацию через byte[],
     * поэтому этот бин не используется. Однако он оставлен как пример и может пригодиться
     * при переходе на автоматическую десериализацию с JsonDeserializer.
     *
     * @param objectMapper бин ObjectMapper (уже настроен в Spring)
     * @return конвертер, способный десериализовать generic-типы
     */
    @Bean
    public SmartMessageConverter smartMessageConverter(ObjectMapper objectMapper) {
        MappingJacksonParameterizedConverter converter = new MappingJacksonParameterizedConverter();
        converter.setObjectMapper(objectMapper);
        // При желании можно настроить converter.setTypePrecedence(), converter.setStrictContentTypeMatch() и т.д.
        return converter;
    }
}