package com.yongdrel.claim.adapter.out.persistence;

import com.yongdrel.claim.domain.model.ClaimStatus;
import com.yongdrel.claim.domain.model.PrizeClaim;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prize_claims")
public class PrizeClaimEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "winner_id", nullable = false, unique = true,
            columnDefinition = "BINARY(16)")
    private UUID winnerId;

    @Column(name = "prize_id", nullable = false,
            columnDefinition = "BINARY(16)")
    private UUID prizeId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "fulfillment_reference", length = 150)
    private String fulfillmentReference;

    @Version
    @Column(nullable = false)
    private long version;

    protected PrizeClaimEntity() {
    }

    public static PrizeClaimEntity fromDomain(PrizeClaim claim) {
        PrizeClaimEntity entity = new PrizeClaimEntity();
        entity.id = claim.id();
        entity.winnerId = claim.winnerId();
        entity.prizeId = claim.prizeId();
        entity.userId = claim.userId();
        entity.status = claim.status();
        entity.idempotencyKey = claim.idempotencyKey();
        entity.submittedAt = claim.submittedAt();
        entity.reviewedAt = claim.reviewedAt();
        entity.approvedAt = claim.approvedAt();
        entity.rejectedAt = claim.rejectedAt();
        entity.fulfilledAt = claim.fulfilledAt();
        entity.rejectionReason = claim.rejectionReason();
        entity.fulfillmentReference = claim.fulfillmentReference();
        entity.version = claim.version();
        return entity;
    }

    public PrizeClaim toDomain() {
        return new PrizeClaim(
                id,
                winnerId,
                prizeId,
                userId,
                status,
                idempotencyKey,
                submittedAt,
                reviewedAt,
                approvedAt,
                rejectedAt,
                fulfilledAt,
                rejectionReason,
                fulfillmentReference,
                version
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public UUID getPrizeId() {
        return prizeId;
    }

    public String getUserId() {
        return userId;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public LocalDateTime getFulfilledAt() {
        return fulfilledAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getFulfillmentReference() {
        return fulfillmentReference;
    }

    public long getVersion() {
        return version;
    }
}