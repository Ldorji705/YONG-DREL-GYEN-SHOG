CREATE TABLE ticket_inventory (
 event_id BINARY(16) NOT NULL PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 capacity INT NOT NULL,
 reserved INT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT chk_inventory_capacity CHECK (capacity >= 0),
 CONSTRAINT chk_inventory_reserved CHECK (reserved >= 0 AND reserved <= capacity)
);
CREATE TABLE ticket_reservations (
 id BINARY(16) NOT NULL PRIMARY KEY,
 tenant_id BINARY(16) NOT NULL,
 event_id BINARY(16) NOT NULL,
 user_id BINARY(16) NOT NULL,
 quantity INT NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL,
 status VARCHAR(16) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 payment_id BINARY(16) NULL,
 CONSTRAINT uk_reservation_idempotency UNIQUE (tenant_id, idempotency_key),
 CONSTRAINT chk_reservation_quantity CHECK (quantity > 0),
 INDEX ix_reservation_expiry (status, expires_at)
);
