package oleborn.orderhub_project.order.domain.dto;

import java.time.LocalDateTime;

public record OrderCreatedEvent(
        Long orderId,
        LocalDateTime timestamp
) {
    public static OrderCreatedEvent of(Long orderId) {
        return new OrderCreatedEvent(orderId, LocalDateTime.now());
    }
}
