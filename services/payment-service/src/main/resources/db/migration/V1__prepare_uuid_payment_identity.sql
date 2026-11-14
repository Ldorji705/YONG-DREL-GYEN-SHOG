ALTER TABLE payments
    MODIFY COLUMN user_id BINARY(16) NOT NULL,
    ADD COLUMN payment_proof_id BINARY(16) NULL,
    ADD COLUMN verified_by BINARY(16) NULL,
    ADD COLUMN verified_at DATETIME(6) NULL,
    ADD COLUMN rejection_reason VARCHAR(500) NULL,
    MODIFY COLUMN status ENUM(
        'CANCELLED',
        'CONFIRMED',
        'FAILED',
        'PENDING',
        'PENDING_VERIFICATION',
        'PROCESSING',
        'REJECTED',
        'REFUNDED'
    ) NOT NULL;
