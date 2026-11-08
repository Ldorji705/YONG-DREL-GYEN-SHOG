
package com.yongdrel.ticket_issuance_service.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketIssuedEvent(
        UUID eventId,
        UUID orderId,
        Long userId,
        UUID lotteryEventId,
        List<UUID> ticketIds,
        Instant issuedAt
) {}
