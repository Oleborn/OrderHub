package oleborn.order_service.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.dictionary.OutboxStatus;
import oleborn.order_service.order.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class DebeziumMetrics {

    private final MeterRegistry registry;
    private final OutboxEventRepository outboxRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${debezium.connect.url}")
    private String connectUrl;

    @Value("${debezium.connector.name}")
    private String connectorName;

    private final AtomicInteger connectorStatus = new AtomicInteger(0);

    @PostConstruct
    public void init() {

        Gauge.builder(
                        "outbox.events.pending",
                        outboxRepository,
                        repo -> repo.countByStatus(OutboxStatus.NEW)
                )
                .description("Pending outbox events")
                .register(registry);

        Gauge.builder(
                        "debezium.connector.status",
                        connectorStatus,
                        AtomicInteger::get
                )
                .description("1=RUNNING, 0=FAILED")
                .register(registry);
    }

    @Scheduled(fixedDelay = 10000)
    public void refreshConnectorStatus() {

        try {

            String url =
                    connectUrl +
                            "/connectors/" +
                            connectorName +
                            "/status";

            JsonNode response =
                    restTemplate.getForObject(
                            url,
                            JsonNode.class
                    );

            String state =
                    response.path("connector")
                            .path("state")
                            .asText();

            connectorStatus.set(
                    "RUNNING".equals(state)
                            ? 1
                            : 0
            );

        } catch (Exception e) {

            connectorStatus.set(0);

            log.warn(
                    "Cannot get Debezium status",
                    e
            );
        }
    }

    public void incrementOutboxCreated() {

        registry.counter("outbox.events.created.total").increment();
    }

    public boolean isConnectorRunning() {
        return connectorStatus.get() == 1;
    }
}