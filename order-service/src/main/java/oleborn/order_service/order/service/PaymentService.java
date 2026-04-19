package oleborn.order_service.order.service;

import feign.FeignException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.client.PaymentClient;
import oleborn.order_service.order.domain.Order;
import oleborn.order_service.order.domain.dto.PaymentRequestDto;
import oleborn.order_service.order.domain.dto.PaymentResponseDto;
import oleborn.order_service.order.exception.PaymentFailedException;
import oleborn.order_service.order.feignclient.PaymentFeignClient;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
//import org.springframework.retry.annotation.CircuitBreaker;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;


@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentClient paymentClient;
    private final PaymentFeignClient paymentFeignClient;

    //стандарт спринг, при использовании @CircuitBreaker, @Retryable не использовать
    @Retryable(
            // При каких исключениях повторять (работает по instanceof)
            retryFor = {
                    WebClientResponseException.class,
                    PaymentFailedException.class
            },

            // При каких исключениях НЕ повторять (даже если подходят под retryFor)
            noRetryFor = {
                    IllegalArgumentException.class
            },

            // Максимальное количество попыток (включая первую)
            maxAttempts = 3,
//
//            // SpEL-выражение для динамического задания maxAttempts (например, из property)
//            maxAttemptsExpression = "#{${retry.max-attempts}}",

            // Настройка задержки между попытками
            backoff = @Backoff(
                    delay = 200,          // начальная задержка (мс)
                    maxDelay = 5000,       // максимальная задержка (ограничитель)
                    multiplier = 2.0,      // множитель для экспоненциального роста
                    random = true          // jitter — случайное отклонение
            )
//
//            // SpEL-выражение для тонкой фильтрации исключений (доступен #root)
//            exceptionExpression = "#{@exceptionChecker.shouldRetry(#root)}",
//
//            // Сохранять состояние между попытками (нужно для circuit breaker)
//            stateful = false,
//
//            // Метка для мониторинга и логирования
//            label = "",
//
//            // Имена Spring-бинов, реализующих RetryListener
//            listeners = {""},
//
//            // Имя метода fallback, вызываемого после исчерпания попыток
//            recover = "",
//
//            // Исключения, при которых fallback НЕ вызывается
//            notRecoverable = {},
//
//            // interceptor — имя бина MethodInterceptor для кастомной логики (редко используется)
//            interceptor = ""
    )

//    @CircuitBreaker(
//            // ----- Параметры, унаследованные от @Retryable (через @AliasFor) -----
//            retryFor = {PaymentFailedException.class},      // Какие исключения считать сбоем
//            noRetryFor = {IllegalArgumentException.class},  // Какие исключения игнорировать
//            maxAttempts = 5,                                // После скольких сбоев подряд breaker откроется
//            label = "",                                     // Метка для мониторинга
//            recover = "",                                   // Метод fallback
//            notRecoverable = {},                            // Исключения, при которых fallback не вызывается
//
//            // ----- Специфичные параметры circuit breaker -----
//            resetTimeout = 30000,                                   // Время (мс) в OPEN до перехода в HALF-OPEN
//            resetTimeoutExpression = "#{${breaker.reset-timeout}}", // SpEL для resetTimeout
//            openTimeout = 5000,                                     // Таймаут нахождения в OPEN (редко используется)
//            openTimeoutExpression = "#{${breaker.open-timeout}}",
//            throwLastExceptionOnExhausted = false                   // Выбрасывать ли исключение после исчерпания
//    )
    public PaymentResponseDto processPaymentStandardSpring(Order savedOrder) {

        RetryContext context = RetrySynchronizationManager.getContext();
        int attempt = context != null ? context.getRetryCount() + 1 : 1;

        log.debug("ПОПЫТКА {} ИЗ 3 <===", attempt);

        try {

            PaymentResponseDto response = paymentClient
                    .processPayment(savedOrder.getId(), savedOrder.getTotalPrice())
                    .block(); //Поток переходит в состояние TIMED_WAITING (ждёт ответа по сети)

            log.info("Попытка {} успешна", attempt);

            return response;

        } catch (WebClientResponseException e) {
            log.error("Попытка {}: Техническая ошибка при вызове payment-service {}", attempt, e.getStatusCode());

            throw new PaymentFailedException("Payment service error: " + e.getStatusText());
        }
    }

    @CircuitBreaker(
            name = "paymentService",                //Имя экземпляра, должно соответствовать имени в конфигурации
            fallbackMethod = "paymentFallback"      //Метод, который вызовется при открытом breaker'е или исключении
                                                    //для асинхронного взаимодействия должен возвращать CompletableFuture<PaymentResponseDto>
    )
    //так как advice в прокси от CircuitBreaker выше всех - он последний кто реагирует на ошибку и идет в paymentFallback
    @TimeLimiter(name = "paymentService")
    @Retry(name = "paymentService")
    @Bulkhead(name = "paymentService")
    public CompletableFuture<PaymentResponseDto> processPaymentResilience4j(Order savedOrder) {
        return paymentClient
                .processPayment(savedOrder.getId(), savedOrder.getTotalPrice())
                .toFuture()
                .exceptionally(throwable -> {

                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

                    log.error("Техническая ошибка при вызове payment-service: {}", cause.getMessage());

                    throw new PaymentFailedException("Payment service error: " + cause.getMessage());

                });
    }

    @CircuitBreaker(
            name = "paymentService",
            fallbackMethod = "paymentFallback"
    )
    @Retry(name = "paymentService")
    @Bulkhead(name = "paymentService", fallbackMethod = "paymentFallback")
    public PaymentResponseDto processPaymentFeign(Order savedOrder) {
        try {
            PaymentResponseDto response = paymentFeignClient.processPayment(
                    new PaymentRequestDto(savedOrder.getId(), savedOrder.getTotalPrice())
            );

            log.debug("Успешно обработана оплата через feign-клиента. Ответ: {}", response);

            return response;

        } catch (FeignException.FeignClientException e) {
            log.error("Техническая ошибка при вызове payment-service через feign-клиента");
            throw new PaymentFailedException("Payment service error: " + e.getMessage());
        }
    }

    public CompletableFuture<PaymentResponseDto> paymentFallback(Order savedOrder, Throwable t) {

        log.warn("Fallback для заказа: {}, по причине: {}", savedOrder.getId(), t.getMessage());

        String userMessage = getUserMessage(t);

        return CompletableFuture.completedFuture(
                PaymentResponseDto.builder()
                        .isSuccessful(true)
                        .requiresPendingProcessing(true)
                        .message(userMessage)
                        .build()
        );

    }

    private String getUserMessage(Throwable t) {
        if (t instanceof TimeoutException) {
            return "Платёжный сервис временно недоступен. Заказ будет обработан автоматически.";
        } else if (t instanceof CallNotPermittedException) {
            return "Сервис оплаты перегружен. Ваш заказ в очереди на обработку.";
        }
        return "Заказ принят. Статус оплаты будет обновлён позже.";
    }
}