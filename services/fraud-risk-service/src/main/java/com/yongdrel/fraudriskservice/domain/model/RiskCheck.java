package com.yongdrel.fraudriskservice.domain.model;

import java.time.Instant;

/** A stored risk check: the submission, the assessment and (if raised) the flag it produced. */
public record RiskCheck(
        Long id,
        String idempotencyKey,
        TransactionDetails transaction,
        RiskAssessment assessment,
        Long flagId,
        Instant createdAt
) {

    public RiskCheck withFlagId(Long newFlagId) {
        return new RiskCheck(id, idempotencyKey, transaction, assessment, newFlagId, createdAt);
    }
}
