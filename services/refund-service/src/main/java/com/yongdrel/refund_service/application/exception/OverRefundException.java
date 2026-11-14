package com.yongdrel.refund_service.application.exception;

/** Requested amount is more than what remains refundable (422). */
public class OverRefundException extends RuntimeException {

    public OverRefundException(String message) {
        super(message);
    }
}
