CREATE TABLE notifications (
    id BINARY(16) NOT NULL,
    recipient_id BIGINT NOT NULL,
    template VARCHAR(80) NOT NULL,
    event_id BINARY(16) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uk_notification_event_template_channel
        UNIQUE (event_id, template, channel),

    CONSTRAINT chk_notification_attempts
        CHECK (attempts >= 0),

    CONSTRAINT chk_notification_channel
        CHECK (channel IN ('EMAIL', 'SMS')),

    CONSTRAINT chk_notification_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),

    INDEX idx_notification_recipient_created
        (recipient_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
