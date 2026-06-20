package oleborn.order_service.order.service;

import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.dictionary.OrderStatus;
import oleborn.order_service.order.dictionary.OutboxStatus;
import oleborn.order_service.order.domain.Order;
import oleborn.order_service.order.domain.OrderItem;
import oleborn.order_service.order.domain.event.OutboxEvent;
import oleborn.order_service.order.domain.dto.CreateOrderRequestDto;
import oleborn.order_service.order.domain.event.OrderCreatedEvent;
import oleborn.order_service.order.exception.NotFoundOrderException;
import oleborn.order_service.order.exception.OrderCreationException;
import oleborn.order_service.order.metrics.annotation.BusinessMetric;
import oleborn.order_service.order.repository.OrderRepository;
import oleborn.order_service.order.repository.OutboxEventRepository;
import oleborn.order_service.outbox.DebeziumMetrics;
import org.slf4j.MDC;
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
    private final PaymentService paymentService;
    private final OutboxEventRepository outboxEventRepository;
    private final DebeziumMetrics debeziumMetrics;


    private final AtomicBoolean failureMode = new AtomicBoolean(false);
    private final Random random = new Random();


    //timeout для защиты от долгих SQL-запросов или залипаний на уровне БД, если метод выполняется дольше 5 секунд — транзакция откатится
    //не контролирует вызовы внешних сервисов, только бд
    @Transactional(timeout = 30)
    @BusinessMetric(
            value = "orders.created",
            tags = {"operation=create", "type=write"}
    )
    @Observed(name = "order.creation", contextualName = "create-order")
    @SneakyThrows
    public Order createOrder(CreateOrderRequestDto request) {

        log.debug("В метод createOrder получен запрос: {}", request);

        try {

            runFail();

            List<OrderItem> items = request.items().stream()
                    .map(item -> new OrderItem(
                            item.productId(),
                            item.productName(),
                            item.quantity(),
                            item.price()
                    ))
                    .toList();

            Order order = new Order(items);

            //сначала всегда PENDING, потому что не знаем как закончится
            order.setStatus(OrderStatus.AWAITING_PAYMENT);

            Order savedOrder = orderRepository.saveAndFlush(order);

            log.info("Заказ {} сохранен с статусом AWAITING_PAYMENT", savedOrder.getId());

            // Сохраняем в outbox
            String traceId = Span.current().getSpanContext().getTraceId();
            String spanId = Span.current().getSpanContext().getSpanId();
            String traceparent = String.format("00-%s-%s-01", traceId, spanId); // формат W3C

            OrderCreatedEvent orderCreatedEvent = OrderCreatedEvent.of(
                    savedOrder.getId(),
                    MDC.getCopyOfContextMap()
            );

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("Order")
                    .aggregateId(savedOrder.getId().toString())
                    .eventType("OrderCreatedEvent")
                    .payload(orderCreatedEvent)
                    .traceId(traceId)
                    .spanId(spanId)
                    .status(OutboxStatus.NEW)
                    .traceparent(traceparent)
                    .build();

            outboxEventRepository.save(outboxEvent);
            debeziumMetrics.incrementOutboxCreated();

            log.debug("Отправлено инфо о заказе, id: {}", savedOrder.getId());

            MDC.put("order_id", savedOrder.getId().toString());
            MDC.put("total_amount", savedOrder.getTotalPrice().toString());
            MDC.put("order_status", savedOrder.getStatus().toString());

            // Теги добавятся в order.creation span
            Span.current().setAttribute("order.id", savedOrder.getId());

            return savedOrder;

        } catch (Exception e) {
            Throwable cause = e.getCause();
            log.error("Ошибка при оформлении заказа {}", cause.getMessage());
            throw new OrderCreationException("Error: " + cause.getMessage());
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

    @SneakyThrows
    private void runFail() {
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
    }

    @Transactional
    public void completeOrder(Long orderId) {

        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new OrderCreationException("Order not found: " + orderId)
        );

        // Проверяем, что заказ в ожидании оплаты
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            log.warn("Order {} is not in AWAITING_PAYMENT state (current: {}), skipping", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAID);

        orderRepository.save(order);

        log.info("Order {} completed (PAID)", orderId);
    }

    @Transactional
    public void cancelOrder(Long orderId, String reason) {

        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new OrderCreationException("Order not found: " + orderId)
        );

        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            log.warn("Order {} is not in AWAITING_PAYMENT state (current: {}), skipping", orderId, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        log.info("Order {} cancelled due to: {}", orderId, reason);
    }
}
