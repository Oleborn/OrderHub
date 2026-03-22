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

    private final TracingTaskDecorator tracingTaskDecorator;

    @Override
    public Executor getAsyncExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);

        executor.setMaxPoolSize(10);

        executor.setQueueCapacity(100);

        executor.setThreadNamePrefix("async-");

        executor.setWaitForTasksToCompleteOnShutdown(true);

        executor.setTaskDecorator(tracingTaskDecorator);

        executor.initialize();

        return executor;
    }
}
