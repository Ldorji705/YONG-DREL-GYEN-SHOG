package com.yongdrel.payment_service.domain.model;

public enum PaymentStatus {
    PENDING,
    PENDING_VERIFICATION,
    PROCESSING,
    CONFIRMED,
    REJECTED,
    FAILED,
    CANCELLED,
    REFUNDED
}
