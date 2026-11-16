package com.yongdrel.payment_service.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public record TrustedOrder(
        UUID orderId,
        UUID lotteryEventId,
        UUID userId,
        Integer quantity,
        BigDecimal amount,
        String currency,
        String status,
        UUID tenantId
) {
    public TrustedOrder(UUID orderId, UUID lotteryEventId, UUID userId,
            Integer quantity, BigDecimal amount, String currency, String status) {
        this(orderId, lotteryEventId, userId, quantity, amount, currency, status, null);
    }
}
