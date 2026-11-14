package com.yongdrel.fraudriskservice.application.port.in;

import java.math.BigDecimal;

public record AssessTransactionCommand(
        Long tenantId,
        Long eventId,
        Long orderId,
        Long buyerId,
        String paymentReference,
        String proofImageHash,
        BigDecimal amount,
        BigDecimal expectedAmount
) {
}
