package oleborn.order_service.order.service;

import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.dictionary.OrderStatus;
import oleborn.order_service.order.domain.Order;
import oleborn.order_service.order.domain.OrderItem;
import oleborn.order_service.order.domain.dto.CreateOrderRequestDto;
import oleborn.order_service.order.domain.dto.OrderCreatedEvent;
import oleborn.order_service.order.domain.dto.PaymentResponseDto;
import oleborn.order_service.order.exception.NotFoundOrderException;
import oleborn.order_service.order.exception.OrderCreationException;
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
            order.setStatus(OrderStatus.PENDING);

            Order savedOrder = orderRepository.saveAndFlush(order);

            log.info("Заказ {} создан со статусом PENDING", savedOrder.getId());

//            //синхронный вариант
//            paymentService.processPaymentFeign(savedOrder);

            //асинхронный вариант
            PaymentResponseDto response = paymentService.processPaymentResilience4j(savedOrder).get();

            if (response.requiresPendingProcessing()) {

                orderRepository.save(savedOrder);

                log.info("Заказ {} сохранен с статусом PENDING", savedOrder.getId());

            } else if (response.isSuccessful()) {

                savedOrder.setStatus(OrderStatus.PAID);
                orderRepository.save(savedOrder);

                log.info("Оплата заказа {} успешно проведена", savedOrder.getId());

                eventPublisher.publishEvent(OrderCreatedEvent.of(
                                savedOrder.getId(),
                                MDC.getCopyOfContextMap()
                        )
                );

                log.debug("Отправлено инфо о заказе, id: {}", savedOrder.getId());

            } else {
                // Бизнес-ошибка (недостаточно средств и т.п.)
                savedOrder.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(savedOrder);

                log.warn("Бизнес-ошибка оплаты заказа {}: {}", savedOrder.getId(), response.message());
            }

            MDC.put("order_id", savedOrder.getId().toString());
            MDC.put("total_amount", savedOrder.getTotalPrice().toString());
            MDC.put("order_status", savedOrder.getStatus().toString());

            log.debug("Все успешно сохранено");

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
}
