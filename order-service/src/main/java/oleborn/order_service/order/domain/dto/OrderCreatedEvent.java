package oleborn.order_service.order.domain.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record OrderCreatedEvent(
        Long orderId,
        Map<String, String> context,
        LocalDateTime timestamp
) {
    public static OrderCreatedEvent of(Long orderId, Map<String, String> context) {
        return new OrderCreatedEvent(
                orderId,
                context,
                LocalDateTime.now()
        );
    }
}
