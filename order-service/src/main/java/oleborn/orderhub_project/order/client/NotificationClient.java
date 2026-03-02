package oleborn.orderhub_project.order.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.orderhub_project.order.domain.dto.NotificationRequest;
import oleborn.orderhub_project.order.domain.dto.OrderCreatedEvent;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationClient {

    private final WebClient webClient;

    @Async
    public void notifyOrderCreated(OrderCreatedEvent event) {

        Map<String, String> mdcContext = event.mdcContext();

        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }

        Long orderId = event.orderId();

        try {

            log.info("Отправка уведомления для заказа {} с traceId: {}, сумма: {}",
                    orderId,
                    MDC.get("traceId"),
                    MDC.get("total_amount")
            );

            NotificationRequest request = new NotificationRequest(orderId, "CREATED");

            webClient.post()
                    .uri("/api/notifications")
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block(); // в текущем стеке блокируем — нормально
        } finally {
            MDC.clear(); // очищаем в асинхронном потоке
        }
    }
}