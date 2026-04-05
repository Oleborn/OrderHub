package oleborn.order_service.order.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

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

////    Нет метрик, нет интеграции с Retry/Circuit Breaker, статическая настройка.
////    вариант настройки timeout через клиента
//    @Bean
//    public WebClient paymentWebClient(WebClient.Builder builder) {
//
//        HttpClient httpClient = HttpClient.create()
//                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)  // 1 сек на соединение
//                .responseTimeout(Duration.ofSeconds(2));              // 2 сек на ожидание ответа
//
//        return builder
//                .clientConnector(new ReactorClientHttpConnector(httpClient))
//                .baseUrl(paymentUrl)
//                .build();
//    }

//    настройка для RestTemplate

//    @Bean
//    public RestTemplate restTemplate() {
//        RequestConfig config = RequestConfig.custom()
//                .setConnectTimeout(1000)   // таймаут соединения (мс)
//                .setConnectionRequestTimeout(1000)  // ожидание из пула
//                .setSocketTimeout(2000)    // ожидание данных (мс)
//                .build();
//
//        CloseableHttpClient client = HttpClientBuilder.create()
//                .setDefaultRequestConfig(config)
//                .build();
//
//        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
//    }
}