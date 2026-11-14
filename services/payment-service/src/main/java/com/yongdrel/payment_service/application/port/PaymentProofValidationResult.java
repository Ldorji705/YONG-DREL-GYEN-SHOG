package com.yongdrel.payment_service.application.port;

public record PaymentProofValidationResult(
        boolean exists,
        boolean belongsToStudent,
        boolean belongsToPayment,
        boolean active,
        boolean eligibleForReview
) {}
