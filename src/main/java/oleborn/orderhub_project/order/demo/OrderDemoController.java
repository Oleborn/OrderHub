package oleborn.orderhub_project.order.demo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import oleborn.orderhub_project.order.CreateOrderRequest;
import oleborn.orderhub_project.order.Order;
import oleborn.orderhub_project.order.OrderResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * ДЕМОНСТРАЦИОННЫЙ КОНТРОЛЛЕР - БУДЕТ УДАЛЕН
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/demo/orders")
public class OrderDemoController {

    private final DemoOrderService demoOrderService;

    @PostMapping("/jdbc")
    public ResponseEntity<OrderResponse> createWithJdbc(
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = demoOrderService.createWithJdbc(request);
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.created(URI.create("/demo/orders/jdbc/" + order.getId()))
                .body(response);
    }

    @PostMapping("/jooq")
    public ResponseEntity<OrderResponse> createWithJooq(
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = demoOrderService.createWithJooq(request);
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.created(URI.create("/demo/orders/jooq/" + order.getId()))
                .body(response);
    }
}