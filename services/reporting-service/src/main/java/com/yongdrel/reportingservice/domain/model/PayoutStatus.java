package com.yongdrel.reportingservice.domain.model;

/** Prize payout to a winner: owed (PENDING), handed over (PAID) or voided (CANCELLED). */
public enum PayoutStatus {
    PENDING, PAID, CANCELLED
}
