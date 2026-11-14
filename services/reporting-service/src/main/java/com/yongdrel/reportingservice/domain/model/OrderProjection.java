package com.yongdrel.reportingservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Order Service's ticket order. eventId, tenantId and buyerId are immutable. */
public record OrderProjection(
        Long id,
        Long eventId,
        Long tenantId,
        Long buyerId,
        int quantity,
        BigDecimal amount,
        OrderStatus status,
        Instant createdAt,
        Instant confirmedAt,
        Instant sourceUpdatedAt
) implements Projection {

    public OrderProjection {
        Checks.positive(id, "orderId");
        Checks.positive(eventId, "eventId");
        Checks.positive(tenantId, "tenantId");
        Checks.positive(buyerId, "buyerId");
        Checks.range(quantity, 1, 100, "quantity");
        Checks.money(amount, "amount");
        Checks.notNull(status, "status");
        Checks.notNull(createdAt, "createdAt");
        Checks.notNull(sourceUpdatedAt, "sourceUpdatedAt");
        Checks.require((status == OrderStatus.CONFIRMED) == (confirmedAt != null),
                "confirmedAt is required for CONFIRMED orders and only for them");
        Checks.require(confirmedAt == null || !confirmedAt.isBefore(createdAt), "confirmedAt cannot be before createdAt");
        amount = Checks.norm(amount);
        createdAt = Checks.norm(createdAt);
        confirmedAt = Checks.norm(confirmedAt);
        sourceUpdatedAt = Checks.norm(sourceUpdatedAt);
    }
}
