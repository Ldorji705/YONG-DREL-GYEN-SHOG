package com.yongdrel.payment_service.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public record VerifiedPayment(
        UUID paymentId,
        String providerReference,
        BigDecimal amount,
        String currency,
        boolean successful
) {}
