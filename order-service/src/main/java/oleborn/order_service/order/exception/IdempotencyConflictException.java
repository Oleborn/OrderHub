package oleborn.order_service.order.exception;

import lombok.Getter;
import oleborn.order_service.order.domain.dto.OrderResponseDto;

@Getter
public class IdempotencyConflictException extends RuntimeException {
    private final int status;
    private final OrderResponseDto body;

    public IdempotencyConflictException(int status, OrderResponseDto body) {
        super("Idempotency key already processed");
        this.status = status;
        this.body = body;
    }
}