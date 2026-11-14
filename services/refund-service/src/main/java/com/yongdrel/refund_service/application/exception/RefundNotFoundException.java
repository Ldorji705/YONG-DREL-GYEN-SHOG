package com.yongdrel.refund_service.application.exception;

/** Refund does not exist, or it belongs to another user (404 either way, so nothing leaks). */
public class RefundNotFoundException extends RuntimeException {

    public RefundNotFoundException(String message) {
        super(message);
    }
}
