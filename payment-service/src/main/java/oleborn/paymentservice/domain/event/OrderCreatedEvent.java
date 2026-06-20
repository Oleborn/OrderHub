package oleborn.paymentservice.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.Map;

public record OrderCreatedEvent(
        Long orderId,
        Map<String, String> context,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
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