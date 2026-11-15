-- Reporting Service schema (database: yongdrel_reporting).
-- report_* tables are read-only copies (projections) of data owned by other services; ids are THEIR ids.

CREATE TABLE report_events (
    id                BIGINT        NOT NULL COMMENT 'Event Service event id',
    tenant_id         BIGINT        NOT NULL COMMENT 'Tenant Service id (immutable)',
    name              VARCHAR(120)  NOT NULL,
    ticket_price      DECIMAL(12,2) NOT NULL,
    total_tickets     INT           NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    sales_close_at    DATETIME(6)   NULL,
    source_updated_at DATETIME(6)   NOT NULL COMMENT 'version of the source record',
    PRIMARY KEY (id),
    KEY idx_report_events_tenant_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report_orders (
    id                BIGINT        NOT NULL COMMENT 'Order Service order id',
    event_id          BIGINT        NOT NULL,
    tenant_id         BIGINT        NOT NULL,
    buyer_id          BIGINT        NOT NULL COMMENT 'Auth Service user id',
    quantity          INT           NOT NULL,
    amount            DECIMAL(12,2) NOT NULL,
    status            VARCHAR(30)   NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    confirmed_at      DATETIME(6)   NULL,
    source_updated_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_report_orders_tenant_event_created (tenant_id, event_id, created_at),
    KEY idx_report_orders_event_status (event_id, status),
    KEY idx_report_orders_buyer (buyer_id),
    CONSTRAINT fk_report_orders_event FOREIGN KEY (event_id) REFERENCES report_events (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report_prizes (
    id                BIGINT        NOT NULL COMMENT 'Prize Catalog Service prize id',
    event_id          BIGINT        NOT NULL,
    tenant_id         BIGINT        NOT NULL,
    tier              INT           NOT NULL,
    name              VARCHAR(120)  NOT NULL,
    quantity          INT           NOT NULL,
    unit_value        DECIMAL(12,2) NOT NULL,
    source_updated_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_report_prizes_tenant_event (tenant_id, event_id, tier),
    CONSTRAINT fk_report_prizes_event FOREIGN KEY (event_id) REFERENCES report_events (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report_payouts (
    id                BIGINT        NOT NULL COMMENT 'Winner Service winner/payout id',
    event_id          BIGINT        NOT NULL,
    tenant_id         BIGINT        NOT NULL,
    prize_id          BIGINT        NOT NULL,
    winner_id         BIGINT        NOT NULL COMMENT 'Auth Service user id',
    amount            DECIMAL(12,2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    paid_at           DATETIME(6)   NULL,
    source_updated_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_report_payouts_tenant_event_status (tenant_id, event_id, status),
    CONSTRAINT fk_report_payouts_event FOREIGN KEY (event_id) REFERENCES report_events (id),
    CONSTRAINT fk_report_payouts_prize FOREIGN KEY (prize_id) REFERENCES report_prizes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Owned by this service: organizer-defined report filters.
CREATE TABLE saved_reports (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT      NOT NULL,
    owner_id    BIGINT      NOT NULL COMMENT 'organizer user id who created it',
    name        VARCHAR(80) NOT NULL,
    report_type VARCHAR(20) NOT NULL,
    event_id    BIGINT      NULL,
    period_from DATETIME(6) NULL,
    period_to   DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_saved_reports_tenant_name (tenant_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
