package com.yongdrel.reportingservice.domain.model;

import java.time.Instant;

/** Resolved, authorized filters. tenantId null = platform-wide (admins only). from/to filter orders by createdAt. */
public record ReportScope(Long tenantId, Long eventId, Instant from, Instant to) {
}
