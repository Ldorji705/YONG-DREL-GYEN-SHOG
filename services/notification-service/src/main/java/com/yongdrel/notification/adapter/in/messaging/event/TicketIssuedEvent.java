package com.yongdrel.notification.adapter.in.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketIssuedEvent(
        UUID eventId,
        UUID orderId,
        UUID userId,
        UUID lotteryEventId,
        List<UUID> ticketIds,
        Instant issuedAt
) {
}
