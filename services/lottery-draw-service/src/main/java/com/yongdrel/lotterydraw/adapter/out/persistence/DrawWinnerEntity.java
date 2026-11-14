package com.yongdrel.lotterydraw.adapter.out.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "draw_winners",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_draw_ticket",
            columnNames = {"draw_id", "ticket_id"}
        ),
        @UniqueConstraint(
            name = "uk_draw_selection_order",
            columnNames = {"draw_id", "selection_order"}
        )
    }
)
public class DrawWinnerEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draw_id", nullable = false)
    private DrawEntity draw;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "selection_order", nullable = false)
    private int selectionOrder;

    protected DrawWinnerEntity() {}

    public DrawWinnerEntity(
            DrawEntity draw,
            UUID ticketId,
            int selectionOrder) {

        this.id = UUID.randomUUID();
        this.draw = draw;
        this.ticketId = ticketId;
        this.selectionOrder = selectionOrder;
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public int getSelectionOrder() { return selectionOrder; }
}
