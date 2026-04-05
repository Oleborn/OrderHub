package oleborn.order_service.order.config;

import feign.Feign;
import feign.Request;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableFeignClients(basePackages = "oleborn.order_service.order.feignclient")
public class FeignConfig {

    @Bean
    public Feign.Builder feignBuilder() {
        return Feign.builder()
                .options(
                        //настройка timeout feign client
                        new Request.Options(1, TimeUnit.SECONDS, 2L, TimeUnit.SECONDS, true)
                );
    }

}