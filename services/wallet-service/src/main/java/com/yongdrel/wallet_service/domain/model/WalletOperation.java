package com.yongdrel.wallet_service.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One applied credit or debit. Only ledgerRecorded can change afterwards.
 * Implements Persistable so a new row is inserted directly (its UUID is set in Java).
 */
@Entity
@Table(name = "wallet_ops")
public class WalletOperation implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", length = 16, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", length = 16, nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "request_key", length = 100, nullable = false, updatable = false)
    private String requestKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", length = 10, nullable = false, updatable = false)
    private OperationType type;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "ledger_recorded", nullable = false)
    private boolean ledgerRecorded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.Transient
    private boolean isNew;

    protected WalletOperation() {
    }

    public WalletOperation(UUID walletId, String requestKey, OperationType type,
                           BigDecimal amount, BigDecimal balanceAfter, String description) {
        this.id = UUID.randomUUID();
        this.walletId = walletId;
        this.requestKey = requestKey;
        this.type = type;
        this.amount = amount.setScale(4, RoundingMode.UNNECESSARY);
        this.balanceAfter = balanceAfter.setScale(4, RoundingMode.UNNECESSARY);
        this.description = description;
        this.ledgerRecorded = false;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.isNew = true;
    }

    public void markLedgerRecorded() {
        this.ledgerRecorded = true;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @jakarta.persistence.PostPersist
    @jakarta.persistence.PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override public UUID getId() { return id; }
    public UUID getWalletId() { return walletId; }
    public String getRequestKey() { return requestKey; }
    public OperationType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public boolean isLedgerRecorded() { return ledgerRecorded; }
    public Instant getCreatedAt() { return createdAt; }
}
