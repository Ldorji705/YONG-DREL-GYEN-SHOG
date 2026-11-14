package com.yongdrel.refund_service.application.port;

import java.math.BigDecimal;
import java.util.UUID;

/** The parts of a Payment Service payment that a refund needs. */
public record PaymentSnapshot(
        UUID id,
        UUID orderId,
        String userId,
        BigDecimal amount,
        String currency,
        String status
) {
    public boolean isConfirmed() {
        return "CONFIRMED".equals(status);
    }
}
