package com.yongdrel.payment_service.application.port;

import java.util.UUID;

public record PaymentProofValidationRequest(
        UUID paymentId,
        UUID orderId,
        UUID studentId,
        UUID paymentProofId
) {}
