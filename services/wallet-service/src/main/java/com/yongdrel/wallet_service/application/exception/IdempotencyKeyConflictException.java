package com.yongdrel.wallet_service.application.exception;

/** Same Idempotency-Key reused with a different type or amount (409). */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
