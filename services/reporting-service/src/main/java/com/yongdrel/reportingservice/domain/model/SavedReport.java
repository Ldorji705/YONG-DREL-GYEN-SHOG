package com.yongdrel.reportingservice.domain.model;

import com.yongdrel.reportingservice.domain.exception.InvalidReportRequestException;

import java.time.Instant;
import java.util.Objects;

/** A named report definition an organizer can re-run. Unique name per tenant. */
public class SavedReport {

    private final Long id;
    private final Long tenantId;
    private final Long ownerId;
    private String name;
    private ReportType reportType;
    private Long eventId;
    private Instant from;
    private Instant to;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Long version;

    public SavedReport(Long id, Long tenantId, Long ownerId, String name, ReportType reportType, Long eventId,
                       Instant from, Instant to, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.createdAt = createdAt;
        this.version = version;
        apply(name, reportType, eventId, from, to, updatedAt);
    }

    public void update(String newName, ReportType newType, Long newEventId, Instant newFrom, Instant newTo, Instant now) {
        apply(newName, newType, newEventId, newFrom, newTo, now);
    }

    private void apply(String newName, ReportType newType, Long newEventId, Instant newFrom, Instant newTo, Instant now) {
        if (newName == null || newName.isBlank() || newName.trim().length() > 80) {
            throw new InvalidReportRequestException("name must be 1-80 characters");
        }
        if (newType == null) {
            throw new InvalidReportRequestException("reportType is required");
        }
        if (newType == ReportType.EVENT_SUMMARY && newEventId == null) {
            throw new InvalidReportRequestException("EVENT_SUMMARY reports need an eventId");
        }
        ReportPeriod.validate(newFrom, newTo);
        this.name = newName.trim();
        this.reportType = newType;
        this.eventId = newEventId;
        this.from = newFrom;
        this.to = newTo;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }

    public ReportScope scope() {
        return new ReportScope(tenantId, eventId, from, to);
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public ReportType getReportType() { return reportType; }
    public Long getEventId() { return eventId; }
    public Instant getFrom() { return from; }
    public Instant getTo() { return to; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
