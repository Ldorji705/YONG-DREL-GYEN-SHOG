package com.yongdrel.payment_service.application.port;

import java.util.UUID;

public record BankTransactionVerificationRequest(
        UUID paymentId,
        UUID orderId,
        UUID paymentProofId,
        UUID organizerId
) {}
