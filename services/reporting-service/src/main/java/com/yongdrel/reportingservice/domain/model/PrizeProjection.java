package com.yongdrel.reportingservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Prize Catalog Service's prize tier of an event. */
public record PrizeProjection(
        Long id,
        Long eventId,
        Long tenantId,
        int tier,
        String name,
        int quantity,
        BigDecimal unitValue,
        Instant sourceUpdatedAt
) implements Projection {

    public PrizeProjection {
        Checks.positive(id, "prizeId");
        Checks.positive(eventId, "eventId");
        Checks.positive(tenantId, "tenantId");
        Checks.range(tier, 1, 50, "tier");
        Checks.text(name, 120, "name");
        Checks.range(quantity, 1, 1000, "quantity");
        Checks.money(unitValue, "unitValue");
        Checks.notNull(sourceUpdatedAt, "sourceUpdatedAt");
        name = name.trim();
        unitValue = Checks.norm(unitValue);
        sourceUpdatedAt = Checks.norm(sourceUpdatedAt);
    }

    public BigDecimal totalValue() {
        return unitValue.multiply(BigDecimal.valueOf(quantity));
    }
}
