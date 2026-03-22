package oleborn.order_service.order.demo;

import lombok.RequiredArgsConstructor;
import oleborn.order_service.order.domain.dto.CreateOrderRequestDto;
import oleborn.order_service.order.domain.Order;
import oleborn.order_service.order.domain.OrderItem;
import oleborn.order_service.order.demo.repository.JdbcOrderRepository;
import oleborn.order_service.order.demo.repository.JooqOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ДЕМОНСТРАЦИОННЫЙ СЕРВИС
 */
@Service
@RequiredArgsConstructor
public class DemoOrderService {

    private final JdbcOrderRepository jdbcRepository;
    private final JooqOrderRepository jooqRepository;


    @Transactional
    public Order createWithJdbc(CreateOrderRequestDto request) {
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.price()
                ))
                .toList();

        Order order = new Order(items);
        return jdbcRepository.save(order);
    }

    @Transactional
    public Order createWithJooq(CreateOrderRequestDto request) {
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.price()
                ))
                .toList();

        Order order = new Order(items);
        return jooqRepository.save(order);
    }
}