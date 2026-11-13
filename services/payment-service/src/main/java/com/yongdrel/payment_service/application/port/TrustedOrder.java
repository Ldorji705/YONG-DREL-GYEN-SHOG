package com.yongdrel.payment_service.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public record TrustedOrder(
        UUID orderId,
        UUID lotteryEventId,
        Long userId,
        Integer quantity,
        BigDecimal amount,
        String currency,
        String status
) {}
