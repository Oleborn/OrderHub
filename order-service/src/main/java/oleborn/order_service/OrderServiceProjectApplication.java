package oleborn.order_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
//нужен только для стандартного спринг
//@EnableRetry
public class OrderServiceProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceProjectApplication.class, args);
    }

}
