-- Audit Log Service schema (database: yongdrel_audit). Append-only: the application never UPDATEs or DELETEs.

CREATE TABLE audit_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    event_id       CHAR(36)     NOT NULL COMMENT 'producer-supplied UUID, idempotency key',
    tenant_id      BIGINT       NULL     COMMENT 'Tenant Service id; NULL for platform-level events',
    actor_type     VARCHAR(10)  NOT NULL COMMENT 'USER | SERVICE | SYSTEM',
    actor_id       BIGINT       NULL     COMMENT 'Auth Service user id (JWT sub) when actor_type = USER',
    actor_role     VARCHAR(20)  NULL     COMMENT 'STUDENT | ORGANIZER | ADMIN | SERVICE at the time of the action',
    action         VARCHAR(64)  NOT NULL COMMENT 'UPPER_SNAKE_CASE verb, e.g. PAYMENT_VERIFIED',
    outcome        VARCHAR(10)  NOT NULL COMMENT 'SUCCESS | FAILURE | DENIED',
    target_type    VARCHAR(30)  NOT NULL,
    target_id      VARCHAR(64)  NOT NULL COMMENT 'id of the target in its owning service',
    source_service VARCHAR(50)  NOT NULL,
    correlation_id VARCHAR(64)  NOT NULL COMMENT 'ties together all events of one business transaction',
    occurred_at    DATETIME(6)  NOT NULL COMMENT 'when the action happened (producer clock, UTC)',
    recorded_at    DATETIME(6)  NOT NULL COMMENT 'when this service stored it (UTC)',
    details        TEXT         NULL     COMMENT 'canonical JSON object, max 4000 characters',
    integrity_hash CHAR(64)     NOT NULL COMMENT 'SHA-256 of all fields above; detects later tampering',
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_events_event_id (event_id),
    KEY idx_audit_tenant_time (tenant_id, occurred_at),
    KEY idx_audit_actor_time (actor_id, occurred_at),
    KEY idx_audit_correlation (correlation_id, occurred_at),
    KEY idx_audit_target (target_type, target_id),
    KEY idx_audit_action_time (action, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
