-- Fraud & Risk Service schema (database: yongdrel_fraud). Owned exclusively by this service.

CREATE TABLE risk_rules (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT       NULL COMMENT 'NULL = platform default rule',
    tenant_scope   BIGINT       AS (IFNULL(tenant_id, 0)) STORED COMMENT 'lets the unique key treat NULL tenant as one scope',
    rule_type      VARCHAR(50)  NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    threshold      INT          NOT NULL,
    window_minutes INT          NOT NULL DEFAULT 0 COMMENT '0 = all history',
    weight         INT          NOT NULL,
    description    VARCHAR(255) NULL,
    updated_at     DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_rules_scope_type (tenant_scope, rule_type),
    KEY idx_risk_rules_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE risk_checks (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    idempotency_key   CHAR(64)      NOT NULL,
    tenant_id         BIGINT        NOT NULL,
    event_id          BIGINT        NOT NULL,
    order_id          BIGINT        NOT NULL,
    buyer_id          BIGINT        NOT NULL,
    payment_reference VARCHAR(64)   NULL COMMENT 'normalized: upper-case, no spaces/hyphens',
    proof_image_hash  CHAR(64)      NULL COMMENT 'SHA-256 of the payment screenshot',
    amount            DECIMAL(12,2) NOT NULL,
    expected_amount   DECIMAL(12,2) NULL,
    score             INT           NOT NULL,
    risk_level        VARCHAR(10)   NOT NULL,
    decision          VARCHAR(20)   NOT NULL,
    flag_id           BIGINT        NULL,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_checks_idempotency (idempotency_key),
    KEY idx_risk_checks_reference (tenant_id, payment_reference),
    KEY idx_risk_checks_proof (tenant_id, proof_image_hash),
    KEY idx_risk_checks_buyer_time (tenant_id, buyer_id, created_at),
    KEY idx_risk_checks_order (tenant_id, order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE risk_check_findings (
    check_id  BIGINT       NOT NULL,
    rule_type VARCHAR(50)  NOT NULL,
    weight    INT          NOT NULL,
    detail    VARCHAR(255) NULL,
    KEY idx_findings_check (check_id),
    CONSTRAINT fk_findings_check FOREIGN KEY (check_id) REFERENCES risk_checks (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE risk_flags (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    check_id    BIGINT        NOT NULL,
    tenant_id   BIGINT        NOT NULL,
    event_id    BIGINT        NOT NULL,
    order_id    BIGINT        NOT NULL,
    buyer_id    BIGINT        NOT NULL,
    score       INT           NOT NULL,
    risk_level  VARCHAR(10)   NOT NULL,
    reasons     VARCHAR(500)  NOT NULL COMMENT 'comma-separated rule types',
    status      VARCHAR(20)   NOT NULL,
    reviewer_id BIGINT        NULL,
    review_note VARCHAR(1000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    reviewed_at DATETIME(6)   NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_flags_check (check_id),
    KEY idx_risk_flags_tenant_status (tenant_id, status, created_at),
    KEY idx_risk_flags_buyer_status (tenant_id, buyer_id, status),
    KEY idx_risk_flags_order (order_id),
    CONSTRAINT fk_flags_check FOREIGN KEY (check_id) REFERENCES risk_checks (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
