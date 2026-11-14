package com.yongdrel.payment_service.dto;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentEntity;
import com.yongdrel.payment_service.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID lotteryEventId,
        UUID userId,
        Integer quantity,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        UUID paymentProofId,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt
) {

    public static PaymentResponse from(PaymentEntity payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getLotteryEventId(),
                payment.getUserId(),
                payment.getQuantity(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaymentProofId(),
                payment.getCreatedAt(),
                payment.getConfirmedAt()
        );
    }
}
