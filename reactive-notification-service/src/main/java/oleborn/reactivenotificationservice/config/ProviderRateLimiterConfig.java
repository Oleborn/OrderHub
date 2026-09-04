package oleborn.reactivenotificationservice.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Конфигурация RateLimiter для ограничения частоты вызовов внешнего провайдера.
 * <p>
 * RateLimiter — это механизм, который ограничивает количество операций за единицу времени.
 * В отличие от concurrency (flatMap), который ограничивает число ПАРАЛЛЕЛЬНЫХ операций,
 * RateLimiter ограничивает СКОРОСТЬ запуска операций (RPS — Requests Per Second).
 * <p>
 * Это критически важно для соблюдения контрактных лимитов провайдера.
 */
@Configuration
public class ProviderRateLimiterConfig {

    @Value("${app.provider.rate-limit:50}")
    private int rateLimit;

    /**
     * Создаёт бин RateLimiter для ограничения вызовов к провайдеру.
     * <p>
     * Бин получает имя "providerRateLimiter", чтобы его можно было явно
     * внедрять по имени, если в приложении появятся другие RateLimiter'ы.
     */
    @Bean(name = "providerRateLimiter")
    public RateLimiter providerRateLimiter() {

        RateLimiterConfig config = RateLimiterConfig.custom()

                // --------------------------------------------------------------------
                // limitForPeriod(rateLimit) — максимальное количество разрешений за период.
                // --------------------------------------------------------------------
                // Это значение напрямую определяет RPS (Requests Per Second).
                // Если rateLimit = 50, то за каждую секунду разрешено выполнить 50 вызовов.
                // После исчерпания лимита запросы будут блокироваться (или отклоняться)
                // до следующего периода.
                //
                // Важно: это количество ЗАПУСКОВ операций, а не их завершений.
                // RateLimiter срабатывает до вызова, а не после.
                .limitForPeriod(rateLimit)

                // --------------------------------------------------------------------
                // limitRefreshPeriod(Duration.ofSeconds(1)) — период обновления лимита.
                // --------------------------------------------------------------------
                // Определяет, как часто сбрасывается счётчик разрешений.
                // В нашем случае — каждую секунду.
                //
                // Если бы мы поставили Duration.ofMinutes(1), то за минуту можно было бы
                // сделать rateLimit запросов (например, 50 в минуту, а не в секунду).
                // Для RPS-лимита всегда используем 1 секунду.
                .limitRefreshPeriod(Duration.ofSeconds(1))

                // --------------------------------------------------------------------
                // timeoutDuration(Duration.ofMillis(500)) — время ожидания разрешения.
                // --------------------------------------------------------------------
                // Если лимит исчерпан, запрос не отклоняется сразу, а ждёт до 500 мс,
                // пока не освободится разрешение (или не наступит новый период).
                //
                // Без этого параметра запрос либо сразу получает ошибку (режим REJECT),
                // либо ждёт бесконечно (режим BLOCK). Мы выбираем золотую середину:
                // подождать до 500 мс, а затем упасть с ошибкой.
                //
                // Для нашего сценария:
                // - 500 мс — достаточно, чтобы «пережить» кратковременный всплеск.
                // - Если за 500 мс разрешение не появилось — значит, нагрузка слишком
                //   высокая, и лучше быстро упасть, чем висеть вечно.
                //
                // Важно: timeoutDuration должен быть меньше, чем таймаут WebClient,
                // чтобы мы не ждали дольше, чем готов ждать внешний API.
                .timeoutDuration(Duration.ofMillis(500))

                .build();

        // RateLimiter.of("provider", config) — создаёт экземпляр с именем "provider".
        // Имя используется для мониторинга и метрик (через Resilience4j Metrics).
        //
        // Благодаря этому мы сможем видеть в Prometheus:
        //   - сколько запросов прошло через RateLimiter
        //   - сколько было отклонено
        //   - среднее время ожидания разрешения
        //
        // Это критически важно для мониторинга в production.
        return RateLimiter.of("provider", config);
    }
}