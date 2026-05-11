package oleborn.order_service.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.topic.order-create-topic}")
    private String orderCreateTopic;


    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(orderCreateTopic)
                .partitions(3)          //для демонстрации ребаланса
                .replicas(1)            //у нас один брокер
                .build();
    }
}