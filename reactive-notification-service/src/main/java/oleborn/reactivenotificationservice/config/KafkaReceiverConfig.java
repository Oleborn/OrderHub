package oleborn.reactivenotificationservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import oleborn.reactivenotificationservice.model.dto.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.MicrometerConsumerListener;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация реактивного Kafka consumer'а на основе Reactor Kafka.
 * <p>
 * Отвечает за создание:
 * <ul>
 *   <li>{@link ReceiverOptions} — настройки подписки на топик, десериализация, параметры consumer'а</li>
 *   <li>{@link KafkaReceiver} — реактивный обёртка над Kafka consumer'ом</li>
 * </ul>
 * <p>
 * Ключевые особенности:
 * <ul>
 *   <li>Использование {@code ErrorHandlingDeserializer} для защиты от «битых» сообщений</li>
 *   <li>Ручной контроль commit'а ({@code acknowledge()} в consumer'е)</li>
 *   <li>Настройки устойчивости (таймауты, max.poll.records)</li>
 * </ul>
 * <p>
 * Эта конфигурация следует тем же стандартам, что и блокирующие consumer'ы в проекте
 * ({@code KafkaConsumerConfig}), но адаптирована для реактивного стека.
 */
@Configuration
@Slf4j
public class KafkaReceiverConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.topic.order-created}")
    private String orderTopic;

    /**
     * Создаёт бин {@code ReceiverOptions} — основной конфигурационный объект Reactor Kafka.
     * <p>
     * ReceiverOptions объединяет:
     * <ul>
     *   <li>Стандартные Kafka consumer-свойства (как в обычном consumer'е)</li>
     *   <li>Настройки подписки (какие топики слушать)</li>
     *   <li>Настройки коммита (ручной или автоматический)</li>
     * </ul>
     */
    @Bean
    public ReceiverOptions<String, OrderCreatedEvent> receiverOptions(MeterRegistry meterRegistry) {

        // Используем те же параметры, что и в блокирующих consumer'ах,
        // но адаптируем под реактивную модель.
        Map<String, Object> props = new HashMap<>();

        // --- 1.1. Базовые настройки подключения ---

        // Адреса брокеров Kafka
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Идентификатор consumer group — для распределения партиций между инстансами.
        // Все consumer'ы с одинаковым group.id образуют группу и распределяют нагрузку.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "reactive-notification-service-group");

        // С какой позиции начинать чтение, если offset не задан (или истёк retention).
        // "earliest" — с самого начала топика.
        // "latest" — только новые сообщения.
        // В нашем случае мы хотим обработать все накопленные события, поэтому earliest.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // --- 1.2. Управление commit'ами ---

        // Отключаем авто-коммит — будем подтверждать offset вручную через acknowledge().
        // Это гарантирует, что offset будет закоммичен только после полной обработки
        // сообщения (или fallback). Если бы мы включили авто-коммит, offset мог бы
        // закоммититься до того, как запись реально обработана, что привело бы к потере
        // сообщений при сбое.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // --- 1.3. Настройки десериализации (ключ) ---

        // Для ключа используем ErrorHandlingDeserializer, который оборачивает
        // StringDeserializer. Если ключ невозможно десериализовать (битый ключ),
        // он вернёт null вместо выбрасывания исключения. Это защищает consumer
        // от остановки из-за одного плохого сообщения.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        // Внутренний десериализатор для ключа — строковый.
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);

        // --- 1.4. Настройки десериализации (значение) ---

        // Аналогично для значения: ErrorHandlingDeserializer оборачивает JsonDeserializer.
        // Если приходит невалидный JSON (или другой тип), мы получим null вместо ошибки.
        // Это позволяет пропустить битое сообщение и продолжить обработку.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        // Внутренний десериализатор для значения — JSON → OrderCreatedEvent.
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // --- 1.5. Настройки JsonDeserializer ---

        // Указываем fallback-тип, если в заголовке сообщения нет __TypeId__.
        // Это означает: если продюсер не добавил заголовок с типом, десериализовать
        // JSON в OrderCreatedEvent. Это наш основной (и единственный) тип в этом топике.
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedEvent.class);

        // Маппинг типов — позволяет десериализовать разные события в одном топике.
        // Если в будущем в этом топике появятся другие типы событий, мы сможем
        // добавить их сюда. Формат: "ИмяКласса:полное.имя.класса".
        // Благодаря этому JsonDeserializer сможет определить целевой класс по
        // заголовку __TypeId__.
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                """
                        OrderCreatedEvent:oleborn.reactivenotificationservice.model.dto.OrderCreatedEvent
                        """);

        // Доверенные пакеты — список пакетов, из которых разрешено десериализовать классы.
        // Это критически важно для безопасности: злоумышленник не сможет подсунуть
        // вредоносный класс, указав __TypeId__=MyEvilClass. В продакшене НИКОГДА
        // не используйте "*" (разрешить все пакеты).
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "oleborn.reactivenotificationservice.model.dto");

        // --- 1.6. Параметры производительности и устойчивости ---

        // Максимальное количество записей, возвращаемых за один вызов poll().
        // Это аналог batch size. Ограничивает размер батча, который Reactor Kafka
        // получает за один раз, что влияет на:
        //   - скорость чтения (меньше записей = чаше poll)
        //   - размер буфера в памяти (меньше = меньше память)
        // 100 — хороший баланс между производительностью и памятью.
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        // Таймаут сессии (heartbeat) — максимальное время между heartbeats consumer'а.
        // Если consumer не отправляет heartbeat в течение 30 секунд, он считается
        // «мёртвым», и происходит rebalance (перераспределение партиций).
        // В реактивном consumer'е обработка может быть долгой из-за сетевых вызовов,
        // поэтому увеличиваем таймаут, чтобы не вылетать при нормальной работе.
        // 30 секунд — безопасное значение для большинства сценариев.
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);

        // Максимальный интервал между вызовами poll().
        // Если consumer не вызывает poll() в течение указанного времени (10 минут),
        // он считается «зависшим» и исключается из группы.
        // Это важно для реактивного consumer'а: flatMap(concurrency) может обрабатывать
        // сообщения долго, не вызывая poll() (потому что demand не требует новых записей).
        // Устанавливаем 10 минут, чтобы дать достаточно времени на обработку backlog'а
        // без риска rebalance.
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000); // 10 минут


        // Создаём ReceiverOptions с нашими пропертями.
        ReceiverOptions<String, OrderCreatedEvent> options = ReceiverOptions
                .<String, OrderCreatedEvent>create(props)

                // Указываем, какие топики слушать (в нашем случае — один).
                // Collections.singleton(orderTopic) — создаёт Set с одним элементом.
                .subscription(Collections.singleton(orderTopic))

                // --- 2.1. Настройки коммита (commit) ---

                // commitInterval(Duration.ZERO) — отключаем автоматический коммит по расписанию.
                // Мы используем ручной acknowledge() в consumer'е.
                // Если бы мы оставили commitInterval, Reactor Kafka периодически
                // коммитил бы offset автоматически, что могло бы конфликтовать
                // с нашими ручными коммитами (приводя к дублированию или потере сообщений).
                .commitInterval(Duration.ZERO)

                // commitBatchSize(0) — отключаем автоматический коммит по размеру батча.
                // Аналогично commitInterval, мы не хотим, чтобы Reactor Kafka сам
                // решал, когда коммитить.
                .commitBatchSize(0);

        MicrometerConsumerListener consumerListener = new MicrometerConsumerListener(meterRegistry); // <-- Создаём слушатель

        options = options.consumerListener(consumerListener);

        // Логируем успешную настройку — полезно для отладки и мониторинга.
        log.info("Reactive Kafka отправил конфигурации по топику: {}", orderTopic);

        return options;
    }

    /**
     * Создаёт бин {@code KafkaReceiver} — основной класс для реактивного потребления.
     * <p>
     * KafkaReceiver оборачивает стандартного Kafka consumer'а в реактивную обёртку,
     * предоставляя:
     * <ul>
     *   <li>{@code receive()} — возвращает {@code Flux<ReceiverRecord>}</li>
     *   <li>Управление poll'ингом через demand (backpressure)</li>
     *   <li>Интеграцию с реактивными потоками (Reactive Streams)</li>
     * </ul>
     *
     * @param options настроенный ReceiverOptions
     * @return KafkaReceiver, готовый к использованию в consumer'е
     */
    @Bean
    public KafkaReceiver<String, OrderCreatedEvent> kafkaReceiver(
            ReceiverOptions<String, OrderCreatedEvent> options
    ) {
        return KafkaReceiver.create(options);
    }
}