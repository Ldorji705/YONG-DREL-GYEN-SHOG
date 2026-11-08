
package com.yongdrel.ticket_issuance_service.dto;

import com.yongdrel.ticket_issuance_service.adapter.out.persistence.TicketEntity;
import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String ticketNumber,
        UUID eventId,
        UUID orderId,
        Long userId,
        String status,
        Instant issuedAt
) {
    public static TicketResponse from(TicketEntity ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getEventId(),
                ticket.getOrderId(),
                ticket.getUserId(),
                ticket.getStatus().name(),
                ticket.getIssuedAt()
        );
    }
}
