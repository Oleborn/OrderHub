package oleborn.orderhub_project.order;

import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oleborn.orderhub_project.order.domain.Order;
import oleborn.orderhub_project.order.domain.OrderItem;
import oleborn.orderhub_project.order.domain.dto.OrderCreatedEvent;
import oleborn.orderhub_project.order.exception.NotFoundOrderException;
import oleborn.orderhub_project.order.metrics.annotation.BusinessMetric;
import oleborn.orderhub_project.order.repository.OrderRepository;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @BusinessMetric(
            value = "orders.created",
            tags = {"operation=create", "type=write"}
    )
    @Observed(name = "order.creation", contextualName = "create-order")
    @SneakyThrows
    public Order createOrder(CreateOrderRequest request) {

        log.debug("В метод createOrder получен запрос: {}", request);

        //TODO имитация проблемы, потом удалить
        int random = new Random().nextInt(100);

        log.info("Выпало число: {}", random);

        if (random < 30) {
            log.error("Возникли проблемы с сохранением заказа");
            throw new RuntimeException("Ну типа ошибка соединения с бд");
        }

        if (random > 70) {
            Thread.sleep(200);
        }

        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.price()
                ))
                .toList();

        Order order = new Order(items);

        Order savedOrder = orderRepository.saveAndFlush(order);

        try {
            MDC.put("order_id", savedOrder.getId().toString());
            MDC.put("total_amount", savedOrder.calculateTotalAmount().toString());
            MDC.put("order_status", savedOrder.getStatus().name());

            log.info("Отправляем инфо о заказе, id: {}", savedOrder.getId());

            eventPublisher.publishEvent(
                    OrderCreatedEvent.of(
                            savedOrder.getId(),
                            MDC.getCopyOfContextMap()
                    )
            );

            log.debug("Все успешно сохранено");

            // Теги добавятся в order.creation span
            Span.current().setAttribute("order.id", savedOrder.getId());

            return savedOrder;

        } finally {
            // Очищаем только свои поля, trace_id остаётся чтобы не засорять память
            MDC.remove("order_id");
            MDC.remove("total_amount");
            MDC.remove("order_status");
        }
    }

    @Transactional(readOnly = true)
    @BusinessMetric(
            value = "orders.retrieved",
            tags = {"operation=get", "type=read"}
    )
    public Order getOrderWithItems(Long id) {

        log.debug("В метод getOrderWithItems получен запрос поиска order по id: {}", id);

        Order order = orderRepository.findWithItemsById(id).orElseThrow(
                () -> new NotFoundOrderException("Order not found")
        );

        log.debug("Результат успешно найден");

        return order;
    }
}
