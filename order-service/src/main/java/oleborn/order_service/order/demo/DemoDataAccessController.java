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
    public ResponseEntity<OrderResponseDto> createWithJdbc(
            @Valid @RequestBody CreateOrderRequestDto request) {
        Order order = demoOrderService.createWithJdbc(request);
        OrderResponseDto response = OrderResponseDto.from(order);
        
        return ResponseEntity.created(URI.create("/demo/data-access/jdbc/" + order.getId()))
                .body(response);
    }
    
    @PostMapping("/jooq")
    public ResponseEntity<OrderResponseDto> createWithJooq(
            @Valid @RequestBody CreateOrderRequestDto request) {
        Order order = demoOrderService.createWithJooq(request);
        OrderResponseDto response = OrderResponseDto.from(order);
        
        return ResponseEntity.created(URI.create("/demo/data-access/jooq/" + order.getId()))
                .body(response);
    }
}