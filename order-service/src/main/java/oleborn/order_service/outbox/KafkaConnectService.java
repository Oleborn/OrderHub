package oleborn.order_service.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class KafkaConnectService {

    private final String connectUrl;
    private final String connectorName;
    private final String topicsPrefix;
    private final String tableIncludeList;
    private final String outboxRouteTopic;
    private final String pluginName;
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;

    private final RestTemplate restTemplate = new RestTemplate();

    public KafkaConnectService(
            @Value("${debezium.connect.url}") String connectUrl,
            @Value("${debezium.connector.name}") String connectorName,
            @Value("${debezium.connector.topics-prefix}") String topicsPrefix,
            @Value("${debezium.connector.table-include-list}") String tableIncludeList,
            @Value("${debezium.connector.outbox-route-topic}") String outboxRouteTopic,
            @Value("${debezium.connector.plugin-name}") String pluginName,
            @Value("${debezium.database.host}") String dbHost,
            @Value("${debezium.database.port}") int dbPort,
            @Value("${debezium.database.name}") String dbName,
            @Value("${debezium.database.user}") String dbUser,
            @Value("${debezium.database.password}") String dbPassword
    ) {
        this.connectUrl = connectUrl;
        this.connectorName = connectorName;
        this.topicsPrefix = topicsPrefix;
        this.tableIncludeList = tableIncludeList;
        this.outboxRouteTopic = outboxRouteTopic;
        this.pluginName = pluginName;
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public void applyOutboxConnectorConfig() {
        String url = connectUrl + "/connectors/" + connectorName + "/config";
        Map<String, String> config = buildDebeziumConfig();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(config, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
            log.info("Connector config applied (создан или обновлён): {}", response.getBody());
        } catch (HttpClientErrorException e) {
            log.error("Failed to apply connector config: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Failed to configure Debezium connector", e);
        }
    }

    private boolean connectorExists(String name) {
        String url = connectUrl + "/connectors/" + name;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check connector existence", e);
            return false;
        }
    }

    private HttpEntity<Map<String, Object>> buildRequest(String connectorName, Map<String, String> config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", connectorName);
        body.put("config", config);

        return new HttpEntity<>(body, headers);
    }

    private Map<String, String> buildDebeziumConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        config.put("database.hostname", dbHost);
        config.put("database.port", String.valueOf(dbPort));
        config.put("database.user", dbUser);
        config.put("database.password", dbPassword);
        config.put("database.dbname", dbName);
        // --- Настройки топиков Kafka ---
        // Префикс для всех топиков, которые создаёт коннектор (обычно имя сервера / приложения)
        config.put("topic.prefix", topicsPrefix);
        // Какие таблицы отслеживать (в формате "schema.table", можно несколько через запятую)
        config.put("table.include.list", tableIncludeList);
        // --- Механизм репликации PostgreSQL ---
        // Используем встроенный плагин pgoutput (доступен в PostgreSQL 10+, не требует установки расширений)
        config.put("plugin.name", pluginName);

        // --- Трансформации (SMT)
        // Список трансформаций (может быть несколько через запятую)
        config.put("transforms", "outbox");
        // Тип трансформации: встроенный обработчик для паттерна Outbox
        config.put("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter");
        // Имя целевого топика, в который будет отправлено сообщение (статический топик)
        config.put("transforms.outbox.route.topic.replacement", outboxRouteTopic);
        // Дополнительные поля из таблицы outbox, которые нужно положить в заголовки Kafka-сообщения.
        config.put("transforms.outbox.table.fields.additional.placement", "eventtype:header:__TypeId__,traceparent:header:traceparent");
        // Раскрывать JSON-поле payload как тело сообщения, а не как вложенную строку.
        // Если true, сообщение в Kafka будет чистым JSON из payload, без лишней обёртки.
        config.put("transforms.outbox.table.expand.json.payload", "true");
        return config;
    }
}