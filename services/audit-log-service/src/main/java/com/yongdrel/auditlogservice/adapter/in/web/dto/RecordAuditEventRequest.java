package com.yongdrel.auditlogservice.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yongdrel.auditlogservice.adapter.in.common.DetailsJson;
import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.Role;
import com.yongdrel.auditlogservice.domain.model.TargetType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Also the RabbitMQ message body on exchange yongdrel.audit.events. */
public record RecordAuditEventRequest(
        @NotNull UUID eventId,
        @Positive Long tenantId,
        @NotNull ActorType actorType,
        @Positive Long actorId,
        Role actorRole,
        @NotNull @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$", message = "must be UPPER_SNAKE_CASE, 3-64 characters")
        String action,
        @NotNull Outcome outcome,
        @NotNull TargetType targetType,
        @NotNull @Pattern(regexp = "^[A-Za-z0-9._:-]{1,64}$", message = "must be 1-64 characters [A-Za-z0-9._:-]")
        String targetId,
        @NotNull @Pattern(regexp = "^[a-z][a-z0-9-]{2,49}$", message = "must be a lower-case service name")
        String sourceService,
        @Pattern(regexp = "^[A-Za-z0-9._-]{8,64}$", message = "must be 8-64 characters [A-Za-z0-9._-]")
        String correlationId,
        @NotNull Instant occurredAt,
        @Size(max = 50, message = "must have at most 50 keys") Map<String, Object> details
) {

    @JsonIgnore
    @AssertTrue(message = "actorId is required when actorType is USER")
    public boolean isActorIdPresentForUser() {
        return actorType != ActorType.USER || actorId != null;
    }

    public AuditEventDraft toDraft(String effectiveCorrelationId) {
        return new AuditEventDraft(eventId, tenantId, actorType, actorId, actorRole, action, outcome, targetType,
                targetId, sourceService, effectiveCorrelationId, occurredAt, DetailsJson.write(details));
    }
}
