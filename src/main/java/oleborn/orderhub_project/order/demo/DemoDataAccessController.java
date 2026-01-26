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
 * ДЕМОНСТРАЦИОННЫЙ КОНТРОЛЛЕР - БУДЕТ УДАЛЕН ПОСЛЕ ВИДЕО
 * Эндпоинты только для демонстрации разных подходов
 */
@RestController
@RequestMapping("/demo/data-access")
@RequiredArgsConstructor
public class DemoDataAccessController {

    private final DemoOrderService demoOrderService;
    
    // Эндпоинты для демонстрации создания заказа разными способами
    
    @PostMapping("/jdbc")
    public ResponseEntity<OrderResponse> createWithJdbc(
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = demoOrderService.createWithJdbc(request);
        OrderResponse response = OrderResponse.from(order);
        
        return ResponseEntity.created(URI.create("/demo/data-access/jdbc/" + order.getId()))
                .body(response);
    }
    
    @PostMapping("/jooq")
    public ResponseEntity<OrderResponse> createWithJooq(
            @Valid @RequestBody CreateOrderRequest request) {
        Order order = demoOrderService.createWithJooq(request);
        OrderResponse response = OrderResponse.from(order);
        
        return ResponseEntity.created(URI.create("/demo/data-access/jooq/" + order.getId()))
                .body(response);
    }
}