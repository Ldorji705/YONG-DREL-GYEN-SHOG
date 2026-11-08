
package com.yongdrel.ticket_issuance_service.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentConfirmedEvent(
        UUID eventId,
        UUID paymentId,
        UUID orderId,
        UUID lotteryEventId,
        Long userId,
        int quantity,
        Instant confirmedAt
) {}
