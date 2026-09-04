package oleborn.reactivenotificationservice.client;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.reactivenotificationservice.model.dto.OrderCreatedEvent;
import oleborn.reactivenotificationservice.model.dto.ProviderResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


/**
 * Реактивный клиент для вызова внешнего провайдера уведомлений.
 * <p>
 * Объединяет:
 * <ul>
 *   <li>Неблокирующий HTTP-клиент (WebClient на Netty)</li>
 *   <li>RateLimiter для соблюдения RPS-лимита провайдера</li>
 * </ul>
 * <p>
 * Клиент возвращает {@code Mono<ProviderResponse>}, а не выполняет подписку.
 * Это позволяет вызывающему коду (сервису) добавлять retry, fallback,
 * трансформации и управлять реактивным потоком.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationProviderClient {

    private final WebClient webClient;
    private final RateLimiter rateLimiter;

    /**
     * Отправляет уведомление о создании заказа внешнему провайдеру.
     * <p>
     * Вся цепочка операций неблокирующая:
     * <ol>
     *   <li>POST-запрос через WebClient с телом в виде OrderCreatedEvent</li>
     *   <li>Декодирование ответа в ProviderResponse</li>
     *   <li>Применение RateLimiter для контроля частоты запросов</li>
     *   <li>Логирование успеха или ошибки</li>
     * </ol>
     *
     * @param event событие создания заказа (содержит orderId)
     * @return Mono с ответом провайдера; завершается ошибкой, если запрос не удался
     * или был отклонён RateLimiter'ом.
     */
    public Mono<ProviderResponse> sendNotification(OrderCreatedEvent event) {
        log.debug("Отправка уведомления для заказа: {}", event.orderId());

        // WebClient.post() — создаёт реактивный HTTP-запрос с методом POST.
        // Все операции неблокирующие и возвращают Mono.
        return webClient.post()

                // --------------------------------------------------------------------
                // .bodyValue(event) — устанавливает тело запроса.
                // --------------------------------------------------------------------
                // Spring WebFlux автоматически сериализует объект в JSON
                // (используя Jackson). В заголовке будет установлен
                // Content-Type: application/json.
                .bodyValue(event)

                // --------------------------------------------------------------------
                // .retrieve() — получает ответ и начинает обработку.
                // --------------------------------------------------------------------
                // Это альтернатива .exchangeToMono(). retrieve() выбрасывает ошибку
                // для HTTP-статусов 4xx/5xx. В нашем случае мы явно обрабатываем
                // ошибки через doOnError и на уровне сервиса (retry/fallback).
                .retrieve()

                // --------------------------------------------------------------------
                // .bodyToMono(ProviderResponse.class) — декодирует тело ответа в объект.
                // --------------------------------------------------------------------
                // Если ответ пустой или невалидный — будет ошибка.
                // Если провайдер вернул успешный ответ (2xx), мы получим ProviderResponse.
                .bodyToMono(ProviderResponse.class)

                // transformDeferred(RateLimiterOperator.of(rateLimiter)) — обёртывает
                // текущую цепочку Mono в оператор, который перед выполнением запроса
                // проверяет наличие разрешения в RateLimiter.
                //
                // Как работает RateLimiterOperator:
                //   - При подписке на Mono (когда кто-то вызывает subscribe() или
                //     flatMap) оператор пытается получить разрешение у RateLimiter.
                //   - Если разрешение есть — запрос выполняется.
                //   - Если разрешения нет — операция ждёт до timeoutDuration (500 мс).
                //   - Если за это время разрешение не появилось — Mono завершается
                //     ошибкой (RequestNotPermitted).
                //
                // Почему transformDeferred, а не transform?
                //   - transform применяется один раз при сборке цепочки.
                //   - transformDeferred применяется при каждой подписке, что позволяет
                //     правильно обрабатывать состояние RateLimiter (он меняется со временем).
                //
                // Важно: RateLimiter срабатывает ДО отправки HTTP-запроса.
                // Таким образом, мы не даём провайдеру больше запросов, чем разрешено.
                .transformDeferred(RateLimiterOperator.of(rateLimiter))

                // doOnSuccess / doOnError — выполняются, когда Mono завершается
                // соответственно успешно или с ошибкой. Это побочные эффекты,
                // они НЕ меняют результат и не влияют на поток (в отличие от
                // onErrorResume, который может перехватить ошибку).
                .doOnSuccess(resp -> log.info("Уведомление по заказу: {} отправлено, ответ: {}",
                        event.orderId(), resp))
                .doOnError(err -> log.error("Ошибка отправки уведомления по заказу {}",
                        event.orderId(), err));
    }
}