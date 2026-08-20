package oleborn.notificationservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.topic.notification-events}")
    private String notificationEventsTopic;


    @Bean
    public NewTopic notificationSentTopic() {
        return TopicBuilder.name(notificationEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}