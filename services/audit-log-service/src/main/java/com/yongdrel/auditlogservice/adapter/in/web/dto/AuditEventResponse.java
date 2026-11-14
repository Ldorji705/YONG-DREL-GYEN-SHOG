package com.yongdrel.auditlogservice.adapter.in.web.dto;

import com.yongdrel.auditlogservice.adapter.in.common.DetailsJson;
import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.Role;
import com.yongdrel.auditlogservice.domain.model.TargetType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
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
        Map<String, Object> details,
        String integrityHash,
        boolean integrityValid
) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.id(), e.eventId(), e.tenantId(), e.actorType(), e.actorId(), e.actorRole(),
                e.action(), e.outcome(), e.targetType(), e.targetId(), e.sourceService(), e.correlationId(),
                e.occurredAt(), e.recordedAt(), DetailsJson.read(e.detailsJson()), e.integrityHash(), e.isIntact());
    }
}
