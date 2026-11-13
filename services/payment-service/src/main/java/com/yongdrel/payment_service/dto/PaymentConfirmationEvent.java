package com.yongdrel.payment_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentConfirmationEvent(
        UUID eventId,
        UUID paymentId,
        UUID orderId,
        UUID lotteryEventId,
        Long userId,
        int quantity,
        LocalDateTime confirmedAt
) {}
