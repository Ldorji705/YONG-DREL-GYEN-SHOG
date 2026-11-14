package com.yongdrel.fraudriskservice.domain.service;

import java.time.Instant;

/** Read-only view of past activity that the evaluator needs. Implemented by the application layer. */
public interface RiskHistory {

    long countOtherOrdersWithReference(Long tenantId, String paymentReference, Long excludeOrderId, Instant since);

    long countOtherOrdersWithProofHash(Long tenantId, String proofImageHash, Long excludeOrderId, Instant since);

    long countBuyerChecksSince(Long tenantId, Long buyerId, Instant since);

    long countConfirmedFraudFlags(Long tenantId, Long buyerId);
}
