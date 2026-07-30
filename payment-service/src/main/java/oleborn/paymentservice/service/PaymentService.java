package oleborn.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.paymentservice.dictionary.PaymentStatus;
import oleborn.paymentservice.domain.command.ProcessPaymentCommand;
import oleborn.paymentservice.domain.entity.Payment;
import oleborn.paymentservice.domain.event.PaymentCompletedEvent;
import oleborn.paymentservice.domain.event.PaymentFailedEvent;
import oleborn.paymentservice.messaging.producer.PaymentProducer;
import oleborn.paymentservice.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentProducer paymentProducer;
    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void processPayment(ProcessPaymentCommand command) {

        String lockKey = "payment:order:" + command.orderId();

        try {

            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(
                            lockKey,
                            "processing",
                            Duration.ofHours(24)
                    );

            log.info("Redis обработал блокировку, результат: {}", locked);


            if (Boolean.FALSE.equals(locked)) {
                log.info("Заказ {} уже обрабатывается или обработан (Redis), пропускаем", command.orderId());
                return;
            }

            Optional<Payment> existing = paymentRepository.findByOrderId(command.orderId());

            //Проверка в БД (защита от дублей, если Redis потерял ключ)
            if (existing.isPresent()) {
                log.info("Платёж для заказа {} уже обработан (БД), пропускаем повтор", command.orderId());
                return;
            }

            String transactionId = "txn_" + System.currentTimeMillis();

            Payment record = Payment.builder()
                    .orderId(command.orderId())
                    .transactionId(transactionId)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            paymentRepository.saveAndFlush(record);

            paymentProducer.sendPaymentCompletedEvent(
                    new PaymentCompletedEvent(
                            command.orderId(),
                            transactionId,
                            PaymentStatus.COMPLETED.name())
            );

        } catch (DataIntegrityViolationException e) {

            // Гонка на уровне БД – кто-то другой успел сохранить
            log.warn("Платёж для заказа {} уже создан параллельно, пропускаем", command.orderId());
            redisTemplate.delete(lockKey); // удаляем ключ, чтобы не блокировать

        } catch (Exception ex) {

            // Любая другая ошибка – удаляем ключ, чтобы можно было повторить
            redisTemplate.delete(lockKey);

            log.error("Ошибка обработки платежа для заказа {}", command.orderId(), ex);

            paymentProducer.sendPaymentFailedEvent(
                    new PaymentFailedEvent(command.orderId(), ex.getMessage())
            );
        }
    }
}
