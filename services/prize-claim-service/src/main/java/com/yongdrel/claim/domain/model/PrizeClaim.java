package com.yongdrel.claim.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record PrizeClaim(
        UUID id,
        UUID winnerId,
        UUID prizeId,
        String userId,
        ClaimStatus status,
        String idempotencyKey,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime approvedAt,
        LocalDateTime rejectedAt,
        LocalDateTime fulfilledAt,
        String rejectionReason,
        String fulfillmentReference,
        long version
) {
    public PrizeClaim {
        if (id == null) {
            throw new IllegalArgumentException("Claim ID is required");
        }
        if (winnerId == null) {
            throw new IllegalArgumentException("Winner ID is required");
        }
        if (prizeId == null) {
            throw new IllegalArgumentException("Prize ID is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("Claim status is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (submittedAt == null) {
            throw new IllegalArgumentException("Submission time is required");
        }
    }
}