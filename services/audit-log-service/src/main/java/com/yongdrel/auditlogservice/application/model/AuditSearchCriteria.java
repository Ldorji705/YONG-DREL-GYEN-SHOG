package com.yongdrel.auditlogservice.application.model;

import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.TargetType;

import java.time.Instant;

/** Optional filters; null means "any". from/to apply to occurredAt (inclusive / exclusive). */
public record AuditSearchCriteria(
        Long tenantId,
        Long actorId,
        ActorType actorType,
        String action,
        Outcome outcome,
        TargetType targetType,
        String targetId,
        String correlationId,
        String sourceService,
        Instant from,
        Instant to
) {

    public static AuditSearchCriteria none() {
        return new AuditSearchCriteria(null, null, null, null, null, null, null, null, null, null, null);
    }

    public static AuditSearchCriteria forCorrelation(String correlationId) {
        return new AuditSearchCriteria(null, null, null, null, null, null, null, correlationId, null, null, null);
    }

    public AuditSearchCriteria withTenantId(Long value) {
        return new AuditSearchCriteria(value, actorId, actorType, action, outcome, targetType, targetId,
                correlationId, sourceService, from, to);
    }

    public AuditSearchCriteria withActorId(Long value) {
        return new AuditSearchCriteria(tenantId, value, actorType, action, outcome, targetType, targetId,
                correlationId, sourceService, from, to);
    }
}
