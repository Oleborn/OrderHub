package oleborn.order_service.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DebeziumHealthIndicator implements HealthIndicator {

    private final DebeziumMetrics metrics;

    @Override
    public Health health() {

        return metrics.isConnectorRunning()
                ? Health.up().build()
                : Health.down().build();
    }
}