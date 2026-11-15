package com.yongdrel.auditlogservice.domain.model;

import com.yongdrel.auditlogservice.domain.exception.InvalidAuditEventException;
import com.yongdrel.auditlogservice.domain.service.IntegrityHasher;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One immutable, append-only audit record. There is deliberately no way to change it after creation;
 * integrityHash lets anyone detect a row that was altered directly in the database.
 */
public record AuditEvent(
        Long id,
        UUID eventId,
        Long tenantId,
        ActorType actorType,
        Long actorId,
        Role actorRole,
        String action,
        Outcome outcome,
        TargetType targetType,
        String targetId,
        String sourceService,
        String correlationId,
        Instant occurredAt,
        Instant recordedAt,
        String detailsJson,
        String integrityHash
) {

    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    public static final int MAX_DETAILS_LENGTH = 4000;
    public static final Pattern ACTION = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
    public static final Pattern TARGET_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    public static final Pattern SOURCE_SERVICE = Pattern.compile("^[a-z][a-z0-9-]{2,49}$");
    public static final Pattern CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    /** Validates the draft, stamps recordedAt and computes the integrity hash. */
    public static AuditEvent create(AuditEventDraft d, Instant now) {
        require(d.eventId() != null, "eventId is required");
        require(d.actorType() != null, "actorType is required");
        require(d.outcome() != null, "outcome is required");
        require(d.targetType() != null, "targetType is required");
        require(d.occurredAt() != null, "occurredAt is required");
        require(d.tenantId() == null || d.tenantId() > 0, "tenantId must be positive");
        require(d.actorId() == null || d.actorId() > 0, "actorId must be positive");
        require(d.actorType() != ActorType.USER || d.actorId() != null, "actorId is required when actorType is USER");
        require(d.actorType() == ActorType.USER || d.actorRole() == null || d.actorRole() == Role.SERVICE,
                "actorRole only applies to USER actors");
        require(d.actorRole() != Role.ORGANIZER || d.tenantId() != null, "ORGANIZER actions must carry a tenantId");
        require(matches(ACTION, d.action()), "action must be UPPER_SNAKE_CASE, 3-64 characters");
        require(matches(TARGET_ID, d.targetId()), "targetId must be 1-64 characters [A-Za-z0-9._:-]");
        require(matches(SOURCE_SERVICE, d.sourceService()), "sourceService must be a lower-case service name");
        require(matches(CORRELATION_ID, d.correlationId()), "correlationId must be 8-64 characters [A-Za-z0-9._-]");
        require(!d.occurredAt().isAfter(now.plus(MAX_CLOCK_SKEW)), "occurredAt cannot be in the future");
        require(d.detailsJson() == null || d.detailsJson().length() <= MAX_DETAILS_LENGTH,
                "details must not exceed " + MAX_DETAILS_LENGTH + " characters of JSON");

        AuditEvent unsealed = new AuditEvent(null, d.eventId(), d.tenantId(), d.actorType(), d.actorId(),
                d.actorRole(), d.action(), d.outcome(), d.targetType(), d.targetId(), d.sourceService(),
                d.correlationId(), d.occurredAt(), now, d.detailsJson(), null);
        return unsealed.withHash(IntegrityHasher.hash(unsealed));
    }

    /** True if the stored content still matches the hash computed when it was recorded. */
    public boolean isIntact() {
        return integrityHash != null && integrityHash.equals(IntegrityHasher.hash(this));
    }

    public AuditEvent withId(Long newId) {
        return new AuditEvent(newId, eventId, tenantId, actorType, actorId, actorRole, action, outcome, targetType,
                targetId, sourceService, correlationId, occurredAt, recordedAt, detailsJson, integrityHash);
    }

    private AuditEvent withHash(String hash) {
        return new AuditEvent(id, eventId, tenantId, actorType, actorId, actorRole, action, outcome, targetType,
                targetId, sourceService, correlationId, occurredAt, recordedAt, detailsJson, hash);
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidAuditEventException(message);
        }
    }
}
