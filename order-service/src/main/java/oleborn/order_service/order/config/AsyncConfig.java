package oleborn.order_service.order.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final TracingTaskDecorator tracingTaskDecorator; // Декоратор для проброса контекста трассировки в асинхронные задачи

    @Override
    public Executor getAsyncExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Минимальное количество потоков, которые всегда держатся в пуле
        executor.setCorePoolSize(5);

        // Максимальное количество потоков, которое может быть создано при пиковой нагрузке
        executor.setMaxPoolSize(10);

        // Размер очереди задач, ожидающих выполнения, когда все потоки заняты
        executor.setQueueCapacity(100);

        // Префикс имени потоков для удобной идентификации в логах и мониторинге
        executor.setThreadNamePrefix("async-");

        // При завершении приложения ждать завершения всех запущенных задач
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Максимальное время ожидания завершения задач при graceful shutdown (сек)
        executor.setAwaitTerminationSeconds(30);

        // Декоратор задач: позволяет пробросить MDC, traceId и spanId в новые потоки
        executor.setTaskDecorator(tracingTaskDecorator);

        executor.initialize(); // Инициализация пула после настройки
        return executor;
    }
}
