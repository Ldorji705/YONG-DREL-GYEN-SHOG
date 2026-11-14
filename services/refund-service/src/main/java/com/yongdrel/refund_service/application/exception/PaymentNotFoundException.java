package com.yongdrel.refund_service.application.exception;

/** Payment does not exist or is not the caller's (404). */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
