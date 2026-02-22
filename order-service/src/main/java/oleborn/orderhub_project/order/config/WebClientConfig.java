package oleborn.orderhub_project.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${url.notification-service}")
    private String url;

    @Bean
    public WebClient notificationWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(url)
                .build();
    }
}