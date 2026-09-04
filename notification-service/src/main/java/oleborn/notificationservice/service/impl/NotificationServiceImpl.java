package oleborn.notificationservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.notificationservice.dictionary.NotificationStatus;
import oleborn.notificationservice.domain.dto.ProviderResponse;
import oleborn.notificationservice.event.NotificationEvent;
import oleborn.notificationservice.event.NotificationSentEvent;
import oleborn.notificationservice.event.OrderCreatedEvent;
import oleborn.notificationservice.messaging.producer.NotificationProducer;
import oleborn.notificationservice.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationProducer notificationProducer;
    private final RestTemplate restTemplate;

    @Value("${app.provider.url}")
    private String providerUrl;

    @Override
    @Transactional
    public void sendNotification(NotificationEvent event) {

        if (event.orderId() == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        NotificationStatus status;
        if (event.transactionId() != null) {
            status = NotificationStatus.PAID;
        } else {
            status = NotificationStatus.CANCELLED;
        }

        try {
            // Имитируем отправку: передаём event как тело запроса
            ProviderResponse response = restTemplate.postForObject(
                    providerUrl,
                    event,
                    ProviderResponse.class
            );

            if (response != null && response.success()) {
                log.info("Уведомление отправлено провайдеру для заказа {}", event.orderId());
            } else {
                log.warn("Провайдер вернул failure для заказа {}", event.orderId());

            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Ошибка HTTP при вызове провайдера для заказа {}: {}", event.orderId(), e.getStatusCode());
        } catch (Exception e) {
            log.error("Ошибка при вызове провайдера для заказа {}", event.orderId(), e);
        }


        notificationProducer.sendPaymentCompletedEvent(
                new NotificationSentEvent(
                        event.orderId(),
                        status,
                        Instant.now()
                )
        );

        log.debug("Уведомление о результате для заказа {} опубликовано", event.orderId());
    }

    @Override
    @Transactional
    public void sendOrderCreatedNotification(OrderCreatedEvent event) {

        if (event.orderId() == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        // Вызов внешнего провайдера (блокирующий)
        try {
            ProviderResponse response = restTemplate.postForObject(
                    providerUrl,
                    event,
                    ProviderResponse.class
            );

            if (response != null && response.success()) {
                log.info("Уведомление о создании заказа {} отправлено провайдеру", event.orderId());
            } else {
                log.warn("Провайдер вернул failure для заказа {}", event.orderId());
            }
        } catch (Exception e) {
            log.error("Ошибка при вызове провайдера для заказа {}", event.orderId(), e);
        }
    }
}