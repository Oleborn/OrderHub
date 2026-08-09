package oleborn.order_service.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import oleborn.order_service.order.domain.dto.CreateOrderRequestDto;
import oleborn.order_service.order.domain.dto.OrderResponseDto;
import oleborn.order_service.order.service.OrderService;
import oleborn.order_service.order.domain.entity.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody CreateOrderRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        OrderResponseDto response = orderService.createOrder(request, idempotencyKey);

        return ResponseEntity.created(URI.create("/orders/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDto> getOrderWithItems(@PathVariable Long id) {
        OrderResponseDto response = orderService.getOrderWithItems(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/failure-mode")
    public void setFailureMode(@RequestParam boolean enabled) {
        orderService.setFailureMode(enabled);
    }
}
