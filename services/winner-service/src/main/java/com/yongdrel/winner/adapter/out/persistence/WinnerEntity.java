package com.yongdrel.winner.adapter.out.persistence;

import com.yongdrel.winner.domain.model.Winner;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "winners",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_winner_draw_ticket",
                        columnNames = {"draw_id", "ticket_id"}
                )
        },
        indexes = {
                @Index(name = "idx_winner_event", columnList = "event_id"),
                @Index(name = "idx_winner_user", columnList = "user_id")
        }
)
public class WinnerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "draw_id", nullable = false, length = 100)
    private String drawId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "ticket_id", nullable = false, length = 100)
    private String ticketId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "prize_id", nullable = false)
    private UUID prizeId;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    protected WinnerEntity() {
    }

    public WinnerEntity(Winner winner) {
        this.id = winner.id();
        this.drawId = winner.drawId();
        this.eventId = winner.eventId();
        this.ticketId = winner.ticketId();
        this.userId = winner.userId();
        this.prizeId = winner.prizeId();
        this.verifiedAt = winner.verifiedAt();
    }

    public Winner toDomain() {
        return new Winner(
                id,
                drawId,
                eventId,
                ticketId,
                userId,
                prizeId,
                verifiedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public String getDrawId() {
        return drawId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getUserId() {
        return userId;
    }

    public UUID getPrizeId() {
        return prizeId;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
