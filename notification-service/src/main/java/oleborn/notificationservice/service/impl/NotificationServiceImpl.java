package oleborn.notificationservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.notificationservice.dictionary.NotificationStatus;
import oleborn.notificationservice.event.NotificationEvent;
import oleborn.notificationservice.event.NotificationSentEvent;
import oleborn.notificationservice.messaging.producer.NotificationProducer;
import oleborn.notificationservice.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationProducer notificationProducer;

    @Override
    @Transactional
    public void sendNotification(NotificationEvent event) {

        // Бизнес-валидация
        if (event.orderId() == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        NotificationStatus status;

        if (event.transactionId() != null) {
            log.info(
                    """
                     Отправлено уведомление:
                     Заказ: {}, успешно обработан.
                     Статус заказа: {}
                     Id транзакции: {}
                     """,
                    event.orderId(),
                    event.status(),
                    event.transactionId()
            );

            status = NotificationStatus.PAID;
        } else {
            log.info(
                    """
                     Отправлено уведомление:
                     Заказ: {}, не обработан.
                     Статус заказа: {}
                     Причина: {}
                     """,
                    event.orderId(),
                    event.status(),
                    event.reason()
            );

            status = NotificationStatus.CANCELLED;
        }

        notificationProducer.sendPaymentCompletedEvent(
                new NotificationSentEvent(
                        event.orderId(),
                        status,
                        Instant.now()
                )
        );
    }
}
