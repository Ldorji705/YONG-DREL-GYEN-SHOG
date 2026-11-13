package com.yongdrel.payment_service.domain.model;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    REFUNDED
}
