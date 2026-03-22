package oleborn.order_service.order.config;

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

        Span currentSpan = tracer.currentSpan();

        return () -> {
            if (currentSpan != null) {
                try (Tracer.SpanInScope scope = tracer.withSpan(currentSpan)){
                    runnable.run();
                }
            } else {
                runnable.run();
            }
        };
    }
}
