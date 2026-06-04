package oleborn.order_service.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DebeziumConnectorInitializer implements ApplicationRunner {
    private final KafkaConnectService kafkaConnectService;

    @Override
    public void run(ApplicationArguments args) {
        kafkaConnectService.createOutboxConnectorIfNotExists();
    }
}