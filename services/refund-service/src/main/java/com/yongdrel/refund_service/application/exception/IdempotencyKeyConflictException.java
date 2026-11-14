package com.yongdrel.refund_service.application.exception;

/** Same Idempotency-Key reused for a different payment or amount (409). */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
