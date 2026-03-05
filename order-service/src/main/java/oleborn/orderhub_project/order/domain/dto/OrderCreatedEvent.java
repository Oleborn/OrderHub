package oleborn.orderhub_project.order.domain.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record OrderCreatedEvent(
        Long orderId,
        Map<String, String> mdcContext,
        LocalDateTime timestamp
) {
    public static OrderCreatedEvent of(Long orderId, Map<String, String> mdcContext) {
        return new OrderCreatedEvent(
                orderId,
                mdcContext,
                LocalDateTime.now()
        );
    }
}
