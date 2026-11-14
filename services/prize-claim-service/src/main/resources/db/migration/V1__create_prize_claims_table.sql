CREATE TABLE prize_claims (
    id BINARY(16) NOT NULL,
    winner_id BINARY(16) NOT NULL,
    prize_id BINARY(16) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    idempotency_key VARCHAR(100) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6) NULL,
    approved_at DATETIME(6) NULL,
    rejected_at DATETIME(6) NULL,
    fulfilled_at DATETIME(6) NULL,
    rejection_reason VARCHAR(500) NULL,
    fulfillment_reference VARCHAR(150) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_prize_claims PRIMARY KEY (id),
    CONSTRAINT uk_claim_winner UNIQUE (winner_id),
    CONSTRAINT uk_claim_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_claim_user_id ON prize_claims (user_id);
CREATE INDEX idx_claim_status ON prize_claims (status);
CREATE INDEX idx_claim_submitted_at ON prize_claims (submitted_at);
