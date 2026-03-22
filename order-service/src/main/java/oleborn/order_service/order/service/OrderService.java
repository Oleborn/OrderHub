package oleborn.order_service.order.service;

import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.Order;
import oleborn.order_service.order.domain.OrderItem;
import oleborn.order_service.order.domain.dto.CreateOrderRequestDto;
import oleborn.order_service.order.domain.dto.OrderCreatedEvent;
import oleborn.order_service.order.domain.dto.PaymentResponseDto;
import oleborn.order_service.order.exception.NotFoundOrderException;
import oleborn.order_service.order.metrics.annotation.BusinessMetric;
import oleborn.order_service.order.repository.OrderRepository;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentService paymentService;

    private final AtomicBoolean failureMode = new AtomicBoolean(false);
    private final Random random = new Random();

    @Transactional
    @BusinessMetric(
            value = "orders.created",
            tags = {"operation=create", "type=write"}
    )
    @Observed(name = "order.creation", contextualName = "create-order")
    @SneakyThrows
    public Order createOrder(CreateOrderRequestDto request) {

        log.debug("В метод createOrder получен запрос: {}", request);

        if (failureMode.get()) {

            int randomInt = random.nextInt(100);

            log.debug("Выпало число: {}", randomInt);

            if (randomInt < 30) {
                log.error("Возникли проблемы с обработкой сохранения заказа");
                throw new RuntimeException("Типа проблемы с обработкой заказа");
            }

            if (randomInt > 70) {
                log.warn("OrderService замедлился");
                Thread.sleep(200);
            }
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

        PaymentResponseDto response = paymentService.processPaymentResilience4j(savedOrder);

        if (response != null && !response.isSuccessful()) {
            log.warn("Бизнес-ошибка оплаты: {}", response.message());
        }

        log.info("Оплата заказа id: {}, успешно проведена", savedOrder.getId());

        log.debug("Отправляем инфо о заказе, id: {}", savedOrder.getId());

        try {
            MDC.put("order_id", savedOrder.getId().toString());
            MDC.put("total_amount", savedOrder.getTotalPrice().toString());
            MDC.put("order_status", savedOrder.getStatus().toString());

            eventPublisher.publishEvent(OrderCreatedEvent.of(
                            savedOrder.getId(),
                            MDC.getCopyOfContextMap()
                    )
            );

            log.debug("Все успешно сохранено");

            // Теги добавятся в order.creation span
            Span.current().setAttribute("order.id", savedOrder.getId());

            return savedOrder;

        } finally {
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

    public void setFailureMode(boolean enabled) {
        failureMode.set(enabled);
        log.info("Failure mode в OrderService, переключен на: {}", enabled);
    }
}
