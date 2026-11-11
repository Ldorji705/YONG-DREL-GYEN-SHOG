-- Transaction Ledger (SCRUM-253 / SCRUM-293)
-- Append-only: rows are only ever inserted, never updated or deleted.
CREATE TABLE ledger_entries (
    id BINARY(16) NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    occurred_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_ledger_reference
        UNIQUE (reference_id),

    CONSTRAINT chk_ledger_direction
        CHECK (direction IN ('DEBIT', 'CREDIT')),

    CONSTRAINT chk_ledger_amount_positive
        CHECK (amount > 0),

    INDEX idx_ledger_account_time (account_id, occurred_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
