package oleborn.order_service.order.domain.dto;

import oleborn.order_service.order.dictionary.OrderStatus;
import oleborn.order_service.order.domain.Order;
import oleborn.order_service.order.domain.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponseDto(

        Long id,
        OrderStatus status,
        Instant createAt,
        List<OrderItemResponse> items,
        BigDecimal total

) {

    public static OrderResponseDto from(Order order) {

        BigDecimal total = order.getItems().stream()
                .map(orderItem -> orderItem.getPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponseDto(
                order.getId(),
                order.getStatus(),
                order.getCreateAt(),
                items,
                total
        );

    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            int quantity,
            BigDecimal price,
            BigDecimal itemTotal
    ){

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getPrice(),
                    item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
            );
        }

    }

}
