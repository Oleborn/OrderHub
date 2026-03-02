package oleborn.orderhub_project.order.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TracingTaskDecorator implements TaskDecorator {

    private final Tracer tracer;

    @Override
    public Runnable decorate(Runnable runnable) {
        // Сохраняем текущий span
        Span currentSpan = tracer.currentSpan();

        return () -> {
            // Восстанавливаем span в новом потоке
            if (currentSpan != null) {
                // withSpan() возвращает Scope, который нужно закрыть
                try (Tracer.SpanInScope scope = tracer.withSpan(currentSpan)) {
                    // Внутри этого блока currentSpan считается активным
                    runnable.run();
                } // здесь scope закрывается автоматически
            } else {
                runnable.run();
            }
        };
    }
}