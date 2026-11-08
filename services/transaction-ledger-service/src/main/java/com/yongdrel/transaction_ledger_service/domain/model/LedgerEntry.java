package com.yongdrel.transaction_ledger_service.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One append-only row in the transaction ledger.
 * There are no setters and the entity is @Immutable, so Hibernate
 * never issues UPDATE statements for it.
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", length = 16, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", length = 100, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "reference_id", length = 100, nullable = false, updatable = false, unique = true)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", length = 30, nullable = false, updatable = false)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", length = 10, nullable = false, updatable = false)
    private EntryDirection direction;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    /** Required by JPA. */
    protected LedgerEntry() {
    }

    public LedgerEntry(
            String accountId,
            String referenceId,
            SourceType sourceType,
            EntryDirection direction,
            BigDecimal amount,
            String currency,
            String description,
            Instant occurredAt
    ) {
        this.accountId = accountId;
        this.referenceId = referenceId;
        this.sourceType = sourceType;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getReferenceId() { return referenceId; }
    public SourceType getSourceType() { return sourceType; }
    public EntryDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getRecordedAt() { return recordedAt; }
}
