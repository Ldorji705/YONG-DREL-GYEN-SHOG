package com.yongdrel.refund_service.adapter.in.web.dto;

import com.yongdrel.refund_service.domain.model.Refund;
import com.yongdrel.refund_service.domain.model.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID orderId,
        String userId,
        BigDecimal amount,
        String currency,
        String reason,
        RefundStatus status,
        String providerReference,
        String failureReason,
        int attempts,
        Instant createdAt,
        Instant updatedAt
) {
    public static RefundResponse from(Refund r) {
        return new RefundResponse(r.getId(), r.getPaymentId(), r.getOrderId(), r.getUserId(),
                r.getAmount(), r.getCurrency(), r.getReason(), r.getStatus(),
                r.getProviderReference(), r.getFailureReason(), r.getAttempts(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
