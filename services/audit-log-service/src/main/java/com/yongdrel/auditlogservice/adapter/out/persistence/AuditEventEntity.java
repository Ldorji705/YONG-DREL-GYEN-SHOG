package com.yongdrel.auditlogservice.adapter.out.persistence;

import com.yongdrel.auditlogservice.domain.model.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Immutable;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
@Immutable
public class AuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true, updatable = false, columnDefinition = "char(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID eventId;
    @Column(name = "tenant_id", updatable = false)
    private Long tenantId;
    @Enumerated(EnumType.STRING) @Column(name = "actor_type", nullable = false, updatable = false, length = 10)
    private ActorType actorType;
    @Column(name = "actor_id", updatable = false)
    private Long actorId;
    @Enumerated(EnumType.STRING) @Column(name = "actor_role", updatable = false, length = 20)
    private Role actorRole;
    @Column(nullable = false, updatable = false, length = 64) private String action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 10)
    private Outcome outcome;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, updatable = false, length = 30)
    private TargetType targetType;
    @Column(name = "target_id", nullable = false, updatable = false, length = 64) private String targetId;
    @Column(name = "source_service", nullable = false, updatable = false, length = 50) private String sourceService;
    @Column(name = "correlation_id", nullable = false, updatable = false, length = 64) private String correlationId;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;
    @Column(name = "details", columnDefinition = "text", updatable = false) private String detailsJson;
    @Column(name = "integrity_hash", nullable = false, updatable = false, length = 64) private String integrityHash;

    protected AuditEventEntity() {
    }

    private AuditEventEntity(AuditEvent event) {
        this.id = event.id();
        this.eventId = event.eventId();
        this.tenantId = event.tenantId();
        this.actorType = event.actorType();
        this.actorId = event.actorId();
        this.actorRole = event.actorRole();
        this.action = event.action();
        this.outcome = event.outcome();
        this.targetType = event.targetType();
        this.targetId = event.targetId();
        this.sourceService = event.sourceService();
        this.correlationId = event.correlationId();
        this.occurredAt = event.occurredAt();
        this.recordedAt = event.recordedAt();
        this.detailsJson = event.detailsJson();
        this.integrityHash = event.integrityHash();
    }

    static AuditEventEntity from(AuditEvent event) {
        return new AuditEventEntity(event);
    }

    AuditEvent toDomain() {
        return new AuditEvent(id, eventId, tenantId, actorType, actorId, actorRole, action, outcome, targetType,
                targetId, sourceService, correlationId, occurredAt, recordedAt, detailsJson, integrityHash);
    }
}
