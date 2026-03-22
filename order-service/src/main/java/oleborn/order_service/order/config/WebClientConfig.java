package oleborn.order_service.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${url.notification-service}")
    private String notificationUrl;

    @Value("${url.payment-service}")
    private String paymentUrl;

    @Bean
    public WebClient notificationWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(notificationUrl)
                .build();
    }

    @Bean
    public WebClient paymentWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(paymentUrl)
                .build();
    }
}