package com.yongdrel.refund_service.domain.model;

/**
 * PENDING   - accepted, amount reserved, provider not yet confirmed
 * COMPLETED - provider refunded and ledger entry recorded
 * FAILED    - provider or ledger step failed; kept so it can be retried
 */
public enum RefundStatus {
    PENDING,
    COMPLETED,
    FAILED
}
