package oleborn.orderhub_project.order.client;

import lombok.RequiredArgsConstructor;
import oleborn.orderhub_project.order.domain.dto.NotificationRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class NotificationClient {

    private final WebClient webClient;

    public void notifyOrderCreated(Long orderId) {

        NotificationRequest request = new NotificationRequest(orderId, "CREATED");

        webClient.post()
                .uri("/api/notifications")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .block(); // в текущем стеке блокируем — нормально
    }
}