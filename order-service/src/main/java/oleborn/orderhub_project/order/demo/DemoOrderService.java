package oleborn.orderhub_project.order.demo;

import lombok.RequiredArgsConstructor;
import oleborn.orderhub_project.order.CreateOrderRequest;
import oleborn.orderhub_project.order.domain.Order;
import oleborn.orderhub_project.order.domain.OrderItem;
import oleborn.orderhub_project.order.demo.repository.JdbcOrderRepository;
import oleborn.orderhub_project.order.demo.repository.JooqOrderRepository;
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
    public Order createWithJdbc(CreateOrderRequest request) {
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
    public Order createWithJooq(CreateOrderRequest request) {
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