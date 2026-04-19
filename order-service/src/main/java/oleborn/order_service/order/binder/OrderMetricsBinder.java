package oleborn.order_service.order.binder;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.dictionary.OrderStatus;
import oleborn.order_service.order.repository.OrderRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMetricsBinder implements MeterBinder {

    private final OrderRepository orderRepository;

    @Override
    public void bindTo(MeterRegistry registry) {

        //Лямбда вызывается при каждом скрейпе
        Gauge.builder("orders.pending.count", orderRepository,
                        repo -> repo.countByStatus(OrderStatus.PENDING))
                .description("Current number of pending orders")
                .register(registry);


        //Для КАЖДОГО статуса создаём Gauge с лямбдой
        for (OrderStatus status : OrderStatus.values()) {
            Gauge.builder("orders.pending.status.count", orderRepository,
                            repo -> repo.countByStatus(status))
                    .tags("status", status.name())
                    .description("Current number of orders with status: " + status)
                    .register(registry);
        }
    }
}