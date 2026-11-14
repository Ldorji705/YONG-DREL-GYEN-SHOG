
package com.yongdrel.ticket_issuance_service.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import com.yongdrel.ticket_issuance_service.domain.model.TicketStatus;

@Entity
@Table(
    name = "tickets",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_ticket_number",
            columnNames = "ticket_number"
        ),
        @UniqueConstraint(
            name = "uk_order_ticket_index",
            columnNames = {"order_id", "ticket_index"}
        )
    }
)
public class TicketEntity {

    @Id
    private UUID id;

    @Column(name = "ticket_number", nullable = false, unique = true)
    private String ticketNumber;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ticket_index", nullable = false)
    private Integer ticketIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    public TicketEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Integer getTicketIndex() {
        return ticketIndex;
    }

    public void setTicketIndex(Integer ticketIndex) {
        this.ticketIndex = ticketIndex;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }
}
