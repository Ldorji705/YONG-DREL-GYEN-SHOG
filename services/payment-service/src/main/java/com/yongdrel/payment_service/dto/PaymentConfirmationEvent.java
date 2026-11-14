package com.yongdrel.payment_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentConfirmationEvent(
        UUID eventId,
        UUID paymentId,
        UUID orderId,
        UUID lotteryEventId,
        UUID userId,
        int quantity,
        LocalDateTime confirmedAt
) {}
