package oleborn.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.paymentservice.domain.event.OrderCreatedEvent;
import oleborn.paymentservice.domain.event.PaymentCompletedEvent;
import oleborn.paymentservice.domain.event.PaymentFailedEvent;
import oleborn.paymentservice.service.producer.PaymentProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final AtomicBoolean failureMode = new AtomicBoolean(true);

    private final PaymentProducer paymentProducer;

    /**
     * Имитация бизнес-логики оплаты.
     * Здесь может быть вызов внешнего платёжного шлюза, работа с БД и т.д.
     * Для демонстрации используем существующий PaymentController, но можно просто заглушку.
     */
    @Transactional
    public void processPayment(OrderCreatedEvent event) {

        //тут логика оплаты которой пока нет

        //Отправляем результат
        if (failureMode.get()) {
            paymentProducer.sendPaymentCompletedEvent(
                    new PaymentCompletedEvent(
                            event.orderId(),
                            "txn_" + System.currentTimeMillis(),
                            "COMPLETED"
                    )
            );

            log.info("Оплата успешна для заказа {}, отправлено PaymentCompletedEvent", event.orderId());

        } else {
            paymentProducer.sendPaymentFailedEvent(
                    new PaymentFailedEvent(
                            event.orderId(),
                            "У сервиса выходной"
                    )
            );
            log.warn("Оплата не удалась для заказа {}, отправлено PaymentFailedEvent", event.orderId());
        }
    }

    public void setFailureMode(boolean enabled) {

        failureMode.set(enabled);

        log.info("Failure mode в Payment Service, переключен на: {}", enabled);
    }
}
