package oleborn.order_service.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import io.github.resilience4j.bulkhead.BulkheadRegistry;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class Resilience4jConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    @PostConstruct
    public void registerEventListeners() {
        circuitBreakerRegistry.circuitBreaker("paymentService")
                .getEventPublisher()
                .onSuccess(event ->
                        log.info("CB SUCCESS: время выполнения {} ms",
                                event.getElapsedDuration().toMillis()))
                .onError(event ->
                        log.error("CB ERROR: время выполнения {} ms, ошибка: {}",
                                event.getElapsedDuration().toMillis(),
                                event.getThrowable().getMessage()))
                .onStateTransition(event ->
                        log.warn("CB STATE TRANSITION: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onCallNotPermitted(event ->
                        log.warn("CB CALL NOT PERMITTED: вызов заблокирован (breaker открыт)"))
                .onIgnoredError(event ->
                        log.debug("CB IGNORED ERROR: ошибка проигнорирована (не влияет на статистику): {}",
                                event.getThrowable().getMessage()));


        retryRegistry.retry("paymentService")
                .getEventPublisher()
                .onRetry(event ->
                        log.debug("RETRY: попытка {} для {}",
                                event.getNumberOfRetryAttempts(),
                                event.getName()))
                .onSuccess(event ->
                        log.info("SUCCESS: после {} попыток",
                                event.getNumberOfRetryAttempts()))
                .onError(event ->
                        log.error("ERROR: попытка {}, причина: {}",
                                event.getNumberOfRetryAttempts(),
                                event.getLastThrowable().getMessage()));

        bulkheadRegistry.bulkhead("paymentService")
                .getEventPublisher()
                .onCallPermitted(event ->
                        log.debug("BULKHEAD: вызов РАЗРЕШЕН: {}", event))
                .onCallFinished(event ->
                        log.debug("BULKHEAD: вызов ЗАКОНЧЕН: {}", event))
                .onCallRejected(event ->
                        log.warn("BULKHEAD: вызов ОТКЛОНЁН: {}", event));
    }
}