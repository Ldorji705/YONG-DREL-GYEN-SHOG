-- Refund Service (SCRUM-254 / SCRUM-298)

-- One row per refund request.
CREATE TABLE refunds (
    id BINARY(16) NOT NULL,
    payment_id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    reason VARCHAR(255) DEFAULT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    provider_reference VARCHAR(100) DEFAULT NULL,
    failure_reason VARCHAR(255) DEFAULT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    -- Same Idempotency-Key from the same user = same refund (safe retries).
    CONSTRAINT uk_refund_user_idempotency
        UNIQUE (user_id, idempotency_key),

    CONSTRAINT chk_refund_amount_positive
        CHECK (amount > 0),

    CONSTRAINT chk_refund_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

    INDEX idx_refund_payment (payment_id),
    INDEX idx_refund_user (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- Running total per payment. The row is locked (SELECT ... FOR UPDATE) while a
-- refund is checked, so two refunds at once can never exceed the paid amount.
CREATE TABLE payment_refund_balances (
    payment_id BINARY(16) NOT NULL,
    paid_amount DECIMAL(19, 4) NOT NULL,
    reserved_amount DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (payment_id),

    CONSTRAINT chk_balance_not_over_refunded
        CHECK (reserved_amount >= 0 AND reserved_amount <= paid_amount)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
