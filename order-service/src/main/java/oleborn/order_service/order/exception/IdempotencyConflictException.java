package oleborn.order_service.order.exception;

import lombok.Getter;

@Getter
public class IdempotencyConflictException extends RuntimeException {
    private final int status;
    private final String body;

    public IdempotencyConflictException(int status, String body) {
        super("Idempotency key already processed");
        this.status = status;
        this.body = body;
    }
}