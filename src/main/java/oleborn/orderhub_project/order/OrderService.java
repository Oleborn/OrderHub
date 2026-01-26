package oleborn.orderhub_project.order;

import lombok.RequiredArgsConstructor;
import oleborn.orderhub_project.order.exception.NotFoundOrderException;
import oleborn.orderhub_project.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {

        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.price()
                ))
                .toList();

        Order order = new Order(items);

        return orderRepository.save(order);

    }

    @Transactional(readOnly = true)
    public Order getOrderWithItems(Long id) {
        return orderRepository.findWithItemsById(id).orElseThrow(
                () -> new NotFoundOrderException("Order not found")
        );
    }
}
