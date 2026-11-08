
package com.yongdrel.ticket_issuance_service.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Ticket {

    private UUID id;
    private String ticketNumber;
    private UUID eventId;
    private UUID orderId;
    private Long userId;
    private TicketStatus status;
    private Instant issuedAt;

    public Ticket() {
    }

    public Ticket(
            UUID id,
            String ticketNumber,
            UUID eventId,
            UUID orderId,
            Long userId,
            TicketStatus status,
            Instant issuedAt
    ) {
        this.id = id;
        this.ticketNumber = ticketNumber;
        this.eventId = eventId;
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.issuedAt = issuedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
