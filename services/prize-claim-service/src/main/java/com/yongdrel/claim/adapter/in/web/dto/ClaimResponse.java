
package com.yongdrel.claim.adapter.in.web.dto;

import com.yongdrel.claim.domain.model.ClaimStatus;
import com.yongdrel.claim.domain.model.PrizeClaim;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        UUID winnerId,
        UUID prizeId,
        String userId,
        ClaimStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime approvedAt,
        LocalDateTime rejectedAt,
        LocalDateTime fulfilledAt,
        String rejectionReason,
        String fulfillmentReference
) {

    public static ClaimResponse from(PrizeClaim claim) {
        return new ClaimResponse(
                claim.id(),
                claim.winnerId(),
                claim.prizeId(),
                claim.userId(),
                claim.status(),
                claim.submittedAt(),
                claim.reviewedAt(),
                claim.approvedAt(),
                claim.rejectedAt(),
                claim.fulfilledAt(),
                claim.rejectionReason(),
                claim.fulfillmentReference()
        );
    }
}
