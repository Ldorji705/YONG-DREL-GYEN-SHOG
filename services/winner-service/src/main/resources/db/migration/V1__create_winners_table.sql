CREATE TABLE winners (
    id BINARY(16) NOT NULL,
    draw_id VARCHAR(100) NOT NULL,
    event_id BINARY(16) NOT NULL,
    prize_id BINARY(16) NOT NULL,
    ticket_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    verified_at DATETIME(6) DEFAULT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_winner_draw_ticket
        UNIQUE (draw_id, ticket_id),

    INDEX idx_winner_event (event_id),
    INDEX idx_winner_user (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;