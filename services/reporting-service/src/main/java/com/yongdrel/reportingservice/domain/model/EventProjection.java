package com.yongdrel.reportingservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Event Service's lottery event. tenantId is immutable once known. */
public record EventProjection(
        Long id,
        Long tenantId,
        String name,
        BigDecimal ticketPrice,
        int totalTickets,
        EventStatus status,
        Instant salesCloseAt,
        Instant sourceUpdatedAt
) implements Projection {

    public EventProjection {
        Checks.positive(id, "eventId");
        Checks.positive(tenantId, "tenantId");
        Checks.text(name, 120, "name");
        Checks.money(ticketPrice, "ticketPrice");
        Checks.range(totalTickets, 1, 1_000_000, "totalTickets");
        Checks.notNull(status, "status");
        Checks.notNull(sourceUpdatedAt, "sourceUpdatedAt");
        name = name.trim();
        ticketPrice = Checks.norm(ticketPrice);
        salesCloseAt = Checks.norm(salesCloseAt);
        sourceUpdatedAt = Checks.norm(sourceUpdatedAt);
    }
}
