package com.yongdrel.reportingservice.application.model;

import java.time.Instant;

/** Filters as requested by the caller, before tenant scoping. */
public record ReportRequest(Long tenantId, Long eventId, Instant from, Instant to) {

    public static ReportRequest of(Long tenantId, Long eventId) {
        return new ReportRequest(tenantId, eventId, null, null);
    }
}
