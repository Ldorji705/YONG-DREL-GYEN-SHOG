package com.yongdrel.reportingservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Winner Service's prize payout. Id = winner record id. */
public record PayoutProjection(
        Long id,
        Long eventId,
        Long tenantId,
        Long prizeId,
        Long winnerId,
        BigDecimal amount,
        PayoutStatus status,
        Instant paidAt,
        Instant sourceUpdatedAt
) implements Projection {

    public PayoutProjection {
        Checks.positive(id, "payoutId");
        Checks.positive(eventId, "eventId");
        Checks.positive(tenantId, "tenantId");
        Checks.positive(prizeId, "prizeId");
        Checks.positive(winnerId, "winnerId");
        Checks.money(amount, "amount");
        Checks.notNull(status, "status");
        Checks.notNull(sourceUpdatedAt, "sourceUpdatedAt");
        Checks.require((status == PayoutStatus.PAID) == (paidAt != null),
                "paidAt is required for PAID payouts and only for them");
        amount = Checks.norm(amount);
        paidAt = Checks.norm(paidAt);
        sourceUpdatedAt = Checks.norm(sourceUpdatedAt);
    }
}
