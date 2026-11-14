package com.yongdrel.auditlogservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/** An audit event as submitted, before it is validated, timestamped and sealed with its integrity hash. */
public record AuditEventDraft(
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
        String detailsJson
) {
}
