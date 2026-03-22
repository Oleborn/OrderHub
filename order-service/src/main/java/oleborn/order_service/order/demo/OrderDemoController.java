package oleborn.order_service.order.demo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import oleborn.order_service.order.domain.dto.CreateOrderRequestDto;
import oleborn.order_service.order.domain.Order;
import oleborn.order_service.order.domain.dto.OrderResponseDto;
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
    public ResponseEntity<OrderResponseDto> createWithJdbc(
            @Valid @RequestBody CreateOrderRequestDto request) {
        Order order = demoOrderService.createWithJdbc(request);
        OrderResponseDto response = OrderResponseDto.from(order);

        return ResponseEntity.created(URI.create("/demo/orders/jdbc/" + order.getId()))
                .body(response);
    }

    @PostMapping("/jooq")
    public ResponseEntity<OrderResponseDto> createWithJooq(
            @Valid @RequestBody CreateOrderRequestDto request) {
        Order order = demoOrderService.createWithJooq(request);
        OrderResponseDto response = OrderResponseDto.from(order);

        return ResponseEntity.created(URI.create("/demo/orders/jooq/" + order.getId()))
                .body(response);
    }
}