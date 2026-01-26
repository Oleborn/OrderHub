package oleborn.orderhub_project.order;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        Order order = orderService.createOrder(request);
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.created(URI.create("/orders/" + order.getId()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderWithItems(@PathVariable Long id) {
        Order order = orderService.getOrderWithItems(id);
        OrderResponse response = OrderResponse.from(order);
        return ResponseEntity.ok(response);
    }

}
