package com.yongdrel.refund_service.application.exception;

/** Payment is not in a refundable state, e.g. not CONFIRMED (422). */
public class RefundNotAllowedException extends RuntimeException {

    public RefundNotAllowedException(String message) {
        super(message);
    }
}
