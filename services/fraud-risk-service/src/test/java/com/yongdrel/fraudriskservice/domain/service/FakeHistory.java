package com.yongdrel.fraudriskservice.domain.service;

import java.time.Instant;

/** Hand-rolled stub: returns fixed counts and records the "since" values it was asked about. */
class FakeHistory implements RiskHistory {

    long referenceCount;
    long proofCount;
    long buyerChecks;
    long confirmedFraud;
    Instant lastReferenceSince;
    Instant lastVelocitySince;
    int calls;

    @Override
    public long countOtherOrdersWithReference(Long tenantId, String ref, Long excludeOrderId, Instant since) {
        calls++;
        lastReferenceSince = since;
        return referenceCount;
    }

    @Override
    public long countOtherOrdersWithProofHash(Long tenantId, String hash, Long excludeOrderId, Instant since) {
        calls++;
        return proofCount;
    }

    @Override
    public long countBuyerChecksSince(Long tenantId, Long buyerId, Instant since) {
        calls++;
        lastVelocitySince = since;
        return buyerChecks;
    }

    @Override
    public long countConfirmedFraudFlags(Long tenantId, Long buyerId) {
        calls++;
        return confirmedFraud;
    }
}
