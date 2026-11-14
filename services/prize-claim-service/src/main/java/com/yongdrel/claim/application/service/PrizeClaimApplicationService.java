
package com.yongdrel.claim.application.service;

import com.yongdrel.claim.application.exception.ClaimNotFoundException;
import com.yongdrel.claim.domain.model.ClaimStatus;
import com.yongdrel.claim.domain.model.PrizeClaim;
import com.yongdrel.claim.domain.port.ClaimReferenceValidator;
import com.yongdrel.claim.domain.port.PrizeClaimRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PrizeClaimApplicationService {

    private final PrizeClaimRepository claimRepository;
    private final ClaimReferenceValidator referenceValidator;

    public PrizeClaimApplicationService(
            PrizeClaimRepository claimRepository,
            ClaimReferenceValidator referenceValidator
    ) {
        this.claimRepository = claimRepository;
        this.referenceValidator = referenceValidator;
    }

    public PrizeClaim submitClaim(
            UUID winnerId,
            UUID prizeId,
            String userId,
            String idempotencyKey,
            String accessToken
    ) {
        if (winnerId == null) {
            throw new IllegalArgumentException("Winner ID is required");
        }

        if (prizeId == null) {
            throw new IllegalArgumentException("Prize ID is required");
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must not exceed 100 characters"
            );
        }

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token is required");
        }

        // Check whether this request has already been processed.
        var existingByKey = claimRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        );

        if (existingByKey.isPresent()) {
            PrizeClaim existing = existingByKey.get();

            if (!existing.winnerId().equals(winnerId)
                    || !existing.prizeId().equals(prizeId)) {
                throw new IllegalStateException(
                        "Idempotency key has already been used for another claim"
                );
            }

            return existing;
        }

        // Prevent multiple claims for the same winner.
        if (claimRepository.existsByWinnerId(winnerId)) {
            throw new IllegalStateException(
                    "A claim already exists for this winner"
            );
        }

        // Validate winner and prize references with external services.
        referenceValidator.validate(
                winnerId,
                prizeId,
                userId,
                accessToken
        );

        LocalDateTime now = LocalDateTime.now();

        PrizeClaim claim = new PrizeClaim(
                UUID.randomUUID(),
                winnerId,
                prizeId,
                userId,
                ClaimStatus.SUBMITTED,
                idempotencyKey,
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                0
        );

        return claimRepository.save(claim);
    }

    @Transactional(readOnly = true)
    public PrizeClaim getClaim(UUID claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(
                        "Prize claim not found: " + claimId
                ));
    }

    @Transactional(readOnly = true)
    public List<PrizeClaim> getMyClaims(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        return claimRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<PrizeClaim> getAllClaims() {
        return claimRepository.findAll();
    }

    public PrizeClaim startReview(UUID claimId) {
        PrizeClaim claim = getClaim(claimId);

        requireStatus(claim, ClaimStatus.SUBMITTED);

        PrizeClaim updatedClaim = new PrizeClaim(
                claim.id(),
                claim.winnerId(),
                claim.prizeId(),
                claim.userId(),
                ClaimStatus.UNDER_REVIEW,
                claim.idempotencyKey(),
                claim.submittedAt(),
                LocalDateTime.now(),
                claim.approvedAt(),
                claim.rejectedAt(),
                claim.fulfilledAt(),
                claim.rejectionReason(),
                claim.fulfillmentReference(),
                claim.version()
        );

        return claimRepository.save(updatedClaim);
    }

    public PrizeClaim approveClaim(UUID claimId) {
        PrizeClaim claim = getClaim(claimId);

        requireStatus(claim, ClaimStatus.UNDER_REVIEW);

        PrizeClaim updatedClaim = new PrizeClaim(
                claim.id(),
                claim.winnerId(),
                claim.prizeId(),
                claim.userId(),
                ClaimStatus.APPROVED,
                claim.idempotencyKey(),
                claim.submittedAt(),
                claim.reviewedAt(),
                LocalDateTime.now(),
                claim.rejectedAt(),
                claim.fulfilledAt(),
                claim.rejectionReason(),
                claim.fulfillmentReference(),
                claim.version()
        );

        return claimRepository.save(updatedClaim);
    }

    public PrizeClaim rejectClaim(
            UUID claimId,
            String rejectionReason
    ) {
        PrizeClaim claim = getClaim(claimId);

        requireStatus(claim, ClaimStatus.UNDER_REVIEW);

        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException(
                    "Rejection reason is required"
            );
        }

        if (rejectionReason.length() > 500) {
            throw new IllegalArgumentException(
                    "Rejection reason must not exceed 500 characters"
            );
        }

        PrizeClaim updatedClaim = new PrizeClaim(
                claim.id(),
                claim.winnerId(),
                claim.prizeId(),
                claim.userId(),
                ClaimStatus.REJECTED,
                claim.idempotencyKey(),
                claim.submittedAt(),
                claim.reviewedAt(),
                claim.approvedAt(),
                LocalDateTime.now(),
                claim.fulfilledAt(),
                rejectionReason,
                claim.fulfillmentReference(),
                claim.version()
        );

        return claimRepository.save(updatedClaim);
    }

    public PrizeClaim fulfillClaim(
            UUID claimId,
            String fulfillmentReference
    ) {
        PrizeClaim claim = getClaim(claimId);

        requireStatus(claim, ClaimStatus.APPROVED);

        if (fulfillmentReference == null
                || fulfillmentReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Fulfillment reference is required"
            );
        }

        if (fulfillmentReference.length() > 150) {
            throw new IllegalArgumentException(
                    "Fulfillment reference must not exceed 150 characters"
            );
        }

        PrizeClaim updatedClaim = new PrizeClaim(
                claim.id(),
                claim.winnerId(),
                claim.prizeId(),
                claim.userId(),
                ClaimStatus.FULFILLED,
                claim.idempotencyKey(),
                claim.submittedAt(),
                claim.reviewedAt(),
                claim.approvedAt(),
                claim.rejectedAt(),
                LocalDateTime.now(),
                claim.rejectionReason(),
                fulfillmentReference,
                claim.version()
        );

        return claimRepository.save(updatedClaim);
    }

    private void requireStatus(
            PrizeClaim claim,
            ClaimStatus expectedStatus
    ) {
        if (claim.status() != expectedStatus) {
            throw new IllegalStateException(
                    "Claim must be in " + expectedStatus
                            + " status, but current status is "
                            + claim.status()
            );
        }
    }
}
