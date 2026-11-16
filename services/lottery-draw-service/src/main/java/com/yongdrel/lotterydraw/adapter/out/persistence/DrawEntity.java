package com.yongdrel.lotterydraw.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "lottery_draws",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_draw_event",
        columnNames = "event_id"
    )
)
public class DrawEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "executed_by", nullable = false)
    private String executedBy;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "eligible_ticket_count", nullable = false)
    private int eligibleTicketCount;

    @Column(name = "winner_count", nullable = false)
    private int winnerCount;

    @Column(nullable = false)
    private String status;
    @Column(name = "seed_commitment", length = 64)
    private String seedCommitment;
    @Column(name = "revealed_seed", length = 64)
    private String revealedSeed;
    @Column(name = "ticket_snapshot_hash", length = 64)
    private String ticketSnapshotHash;
    @Column(name = "ticket_snapshot", columnDefinition = "TEXT")
    private String ticketSnapshot;
    @Column(name = "algorithm_version", length = 64)
    private String algorithmVersion;

    @OneToMany(
        mappedBy = "draw",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("selectionOrder ASC")
    private List<DrawWinnerEntity> winners = new ArrayList<>();

    protected DrawEntity() {}

    public DrawEntity(
            UUID eventId,
            String executedBy,
            int eligibleTicketCount,
            int winnerCount,
            String seedCommitment,
            String revealedSeed,
            String ticketSnapshotHash,
            String ticketSnapshot,
            String algorithmVersion) {

        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.executedBy = executedBy;
        this.executedAt = Instant.now();
        this.eligibleTicketCount = eligibleTicketCount;
        this.winnerCount = winnerCount;
        this.status = revealedSeed == null ? "COMMITTED" : "COMPLETED";
        this.seedCommitment = seedCommitment;
        this.revealedSeed = revealedSeed;
        this.ticketSnapshotHash = ticketSnapshotHash;
        this.ticketSnapshot = ticketSnapshot;
        this.algorithmVersion = algorithmVersion;
    }

    public void complete(String revealedSeed, List<UUID> selectedTicketIds) {
        this.revealedSeed = revealedSeed;
        this.status = "COMPLETED";
        for (int i = 0; i < selectedTicketIds.size(); i++) {
            addWinner(selectedTicketIds.get(i), i + 1);
        }
    }

    public void addWinner(UUID ticketId, int selectionOrder) {
        DrawWinnerEntity winner =
                new DrawWinnerEntity(this, ticketId, selectionOrder);
        winners.add(winner);
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getExecutedBy() { return executedBy; }
    public Instant getExecutedAt() { return executedAt; }
    public int getEligibleTicketCount() { return eligibleTicketCount; }
    public int getWinnerCount() { return winnerCount; }
    public String getStatus() { return status; }
    public String getSeedCommitment() { return seedCommitment; }
    public String getRevealedSeed() { return revealedSeed; }
    public String getTicketSnapshotHash() { return ticketSnapshotHash; }
    public String getTicketSnapshot() { return ticketSnapshot; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public List<DrawWinnerEntity> getWinners() {
        return List.copyOf(winners);
    }
}
