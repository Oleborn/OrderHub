package oleborn.order_service.order.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.NotificationRequest;
import oleborn.order_service.order.domain.dto.OrderCreatedEvent;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationClient {

    private final WebClient notificationWebClient;

    @Async
    public void notifyOrderCreated(OrderCreatedEvent event) {

        Map<String, String> context = event.context();

        if (context != null) {
            MDC.setContextMap(context);
        }

        long orderId = event.orderId();

        try {

            log.info("Отправка уведомления для заказа: {}, с traceID: {}, с суммой: {}",
                    orderId,
                    MDC.get("traceId"),
                    MDC.get("total_amount")
            );

            NotificationRequest request = new NotificationRequest(event.orderId(), "CREATED");

            notificationWebClient.post()
                    .uri("/api/notifications")
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block(); // в текущем стеке блокируем — нормально
        } finally {
            MDC.clear();
        }
    }

}