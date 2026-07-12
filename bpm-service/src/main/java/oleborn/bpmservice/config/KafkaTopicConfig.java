package oleborn.bpmservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.topic.order-events}")
    private String orderEventsTopic;

    @Value("${app.topic.payment-events}")
    private String paymentEventsTopic;

    @Value("${app.topic.payment-commands}")
    private String paymentCommandsTopic;

    @Value("${app.topic.order-commands}")
    private String orderCommandsTopic;

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(orderEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(paymentEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentCommandsTopic() {
        return TopicBuilder.name(paymentCommandsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCommandsTopic() {
        return TopicBuilder.name(orderCommandsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}