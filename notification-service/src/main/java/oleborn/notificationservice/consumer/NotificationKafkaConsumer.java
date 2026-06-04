package oleborn.notificationservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.notificationservice.event.OrderCreatedEvent;
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
public class NotificationKafkaConsumer {

    private final ObjectMapper objectMapper;

    @RetryableTopic(
            // ===== ОСНОВНЫЕ ПАРАМЕТРЫ ПОВТОРОВ =====
            attempts = "3",                                // Общее количество попыток (1 первая + 2 ретрая)
            backoff = @Backoff(                            // Настройка задержек между попытками
                    delay = 1000,                          // начальная задержка (мс)
                    maxDelay = 10000,                      // максимальная задержка (если multiplier>1)
                    multiplier = 2.0,                      // множитель для экспоненциального роста
                    random = true                          // jitter (случайное отклонение)
            ),
            timeout = "60000",                          // Максимальное время всех попыток (после него -> DLT)

            // ===== НАСТРОЙКИ ИМЁН ТОПИКОВ =====
            retryTopicSuffix = "-retry",                   // Суффикс для ретрай-топиков (по умолчанию "-retry")
            dltTopicSuffix = ".DLT",                       // Суффикс для Dead Letter Topic (по умолчанию ".dlt")
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE, // Как именовать ретрай-топики:
            // SUFFIX_WITH_DELAY_VALUE – по задержке (my-topic-retry-1000)
            // SUFFIX_WITH_INDEX_VALUE – по индексу (my-topic-retry-0)
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, // Переиспользовать ли один топик для одинаковых задержек

            // ===== КАКИЕ ИСКЛЮЧЕНИЯ ОБРАБАТЫВАТЬ =====
            //нельзя одновременно использовать include и exclude
            //include = {IOException.class, TimeoutException.class},        // Только эти исключения вызывают ретрай
            exclude = {IllegalArgumentException.class, NullPointerException.class}, // Эти исключения -> сразу в DLT
            // includeNames = { "java.io.IOException" },                  // Альтернатива – строковые имена классов
            // excludeNames = { "java.lang.IllegalArgumentException" },
            traversingCauses = "true",                     // Анализировать ли причину (cause) исключения при проверке include/exclude

            // ===== СТРАТЕГИЯ DLT =====
            dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR, // Всегда ретраить до исчерпания attempts, затем DLT
            // FAIL_ON_ERROR – сразу в DLT (без ретраев) при любом исключении
            // NO_DLT – не создавать DLT, просто выбрасывать исключение после ретраев (опасно)

            // ===== СОЗДАНИЕ ТОПИКОВ =====
            autoCreateTopics = "true",                     // Автоматически создавать ретрай-топики и DLT
            numPartitions = "1",                           // Количество партиций для создаваемых топиков, делаем 1 потому что топик 1
            replicationFactor = "1",                       // Фактор репликации (по умолчанию -1 = использовать настройки брокера), делаем 1 потому что топик 1

            // ===== ИНТЕГРАЦИЯ С LISTENER'ом =====
            listenerContainerFactory = "kafkaListenerContainerFactory", // Фабрика для консьюмеров ретрай- и DLT-топиков (по умолчанию – из @KafkaListener)
            concurrency = "3",                             // Количество потоков для обработки ретрай-топиков (по умолчанию как у основного консьюмера)
            //kafkaTemplate = "retryKafkaTemplate",          // Бин KafkaTemplate для отправки сообщений в ретрай/DLT (должен быть идемпотентным), если указать нужно отдельно создавать бин
            autoStartDltHandler = "true"                   // Автозапуск DLT-контейнера (обычно true)
    )
    @KafkaListener(
            // ===== ОСНОВНЫЕ: ОТКУДА ЧИТАТЬ =====
            topics = "${app.topic.order-create-topic}",                      // Один или несколько топиков (можно массив)
            // topicPattern = "order\\..*",               // Регулярное выражение для подписки на топики
            // topicPartitions = {                        // Ручное назначение партиций (без consumer group)
            //     @TopicPartition(topic = "order.created", partitions = {"0", "1"})
            // },
            groupId = "notification-service-group",        // Идентификатор consumer group (обязательно для надежности)
            // id = "myListener",                          // Уникальный ID контейнера (если не задан, генерируется)
            // idIsGroup = true,                           // Использовать ли id как groupId, если groupId не указан

            // ===== ФАБРИКА И КОНТЕЙНЕР =====
            containerFactory = "kafkaListenerContainerFactory" // Бин фабрики контейнеров (по умолчанию "kafkaListenerContainerFactory")
            // autoStartup = "true",                       // Автозапуск контейнера при старте приложения
            // concurrency = "3",                          // Количество потоков-потребителей (максимум ≤ партиций)

            // ===== КОММИТ OFFSET =====
            // ackMode = "MANUAL_IMMEDIATE",               // Режим подтверждения: MANUAL_IMMEDIATE (ручной), RECORD, BATCH, TIME, COUNT — настраивается в containerFactory
            // properties = {                              // Дополнительные свойства consumer (переопределяют фабрику)
            //     "max.poll.records=10",
            //     "auto.offset.reset=earliest"
            // },

            // ===== ОБРАБОТКА ОШИБОК =====
            //errorHandler = "myErrorHandler"              // Бин KafkaListenerErrorHandler для ошибок в методе, если указать нужно отдельно создавать бин
            // filter = "myFilter",                        // Бин RecordFilterStrategy для фильтрации записей

            // ===== ПРОЧЕЕ =====
            // clientIdPrefix = "notify",                  // Префикс client.id (фактическое имя: notify-1, notify-2...)
            // containerGroup = "notifications",           // Группа контейнеров (для управления через ContainerGroup)
            // contentTypeConverter = "jsonConverter",     // Бин SmartMessageConverter (JSON, Avro)
            // batch = "false",                            // Если true — метод принимает List<ConsumerRecord> (пакетная обработка)
            // info = "static info",                       // Добавляется в заголовок KafkaHeaders.LISTENER_INFO
            // containerPostProcessor = "postProcessor"    // Постпроцессор для модификации контейнера
    )

    //этот вариант будет работать если OrderCreatedEvent идентичен отправляемому
//    public void consume(OrderCreatedEvent event, Acknowledgment acknowledgment) {
//        log.info("Received event for orderId={}", event.orderId());
//
//          //Идемпотентность
//        if (processedEventRepository.existsById(event.orderId())) {
//            log.warn("Duplicate event for order {}, skipping", event.orderId());
//            acknowledgment.acknowledge(); // подтверждаем, чтобы не зациклиться
//            return;
//        }
//
//        try {
//            log.info("Имитация бизнес-логики отправки для orderId={}", event.orderId());
//
//            //Ручной коммит offset
//            acknowledgment.acknowledge();
//            log.info("Событие для заказа: {} обработано и оффсет для него сдвинут(acknowledged) ", event.orderId());
//
//        } catch (Exception e) {
//            log.error("Error processing order {}", event.orderId(), e);
//            // Не вызываем acknowledgment – сообщение попадёт в DLT после всех retry
//            throw new RuntimeException("Processing failed", e);
//        }
//    }

    public void consume(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {

        log.info("Принято сообщение в консьюмере notification");

        try {
            // например, orderId как строка
            byte[] value = record.value();

            OrderCreatedEvent event = objectMapper.readValue(value, OrderCreatedEvent.class);

            // Бизнес-валидация
            if (event.orderId() == null) {
                throw new IllegalArgumentException("orderId must not be null");
            }

            log.info("Имитация бизнес-логики отправки для orderId={}", event.orderId());

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