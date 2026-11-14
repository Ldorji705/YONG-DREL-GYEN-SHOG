-- Wallet Service (SCRUM-252)

-- One wallet per user. The balance can never go negative (CHECK).
CREATE TABLE wallets (
    id BINARY(16) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    currency CHAR(3) NOT NULL,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_wallet_owner
        UNIQUE (owner_id),

    CONSTRAINT chk_wallet_balance_not_negative
        CHECK (balance >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- Every credit/debit applied to a wallet. Append-only.
CREATE TABLE wallet_ops (
    id BINARY(16) NOT NULL,
    wallet_id BINARY(16) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    balance_after DECIMAL(19, 4) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    ledger_recorded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    -- Same request key on the same wallet = same operation (applied only once).
    CONSTRAINT uk_wallet_op_request
        UNIQUE (wallet_id, request_key),

    CONSTRAINT fk_wallet_op_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (id),

    CONSTRAINT chk_wallet_op_type
        CHECK (type IN ('CREDIT', 'DEBIT')),

    CONSTRAINT chk_wallet_op_amount_positive
        CHECK (amount > 0),

    INDEX idx_wallet_op_ledger (ledger_recorded, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
