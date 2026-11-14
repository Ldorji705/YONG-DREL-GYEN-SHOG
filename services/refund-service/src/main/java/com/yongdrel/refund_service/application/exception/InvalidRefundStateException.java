package com.yongdrel.refund_service.application.exception;

/** Action not allowed in the refund's current status, e.g. retrying a COMPLETED refund (409). */
public class InvalidRefundStateException extends RuntimeException {

    public InvalidRefundStateException(String message) {
        super(message);
    }
}
