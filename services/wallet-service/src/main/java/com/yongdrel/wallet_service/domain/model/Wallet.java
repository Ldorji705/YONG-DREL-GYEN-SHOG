package com.yongdrel.wallet_service.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", length = 16, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", length = 100, nullable = false, updatable = false, unique = true)
    private String ownerId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(name = "balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal balance;

    /** Optimistic lock: a second writer that read an old version fails instead of overwriting. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    /** New empty wallet. (In the service, wallets are created atomically by WalletRepository.insertIfAbsent.) */
    public Wallet(String ownerId, String currency) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = BigDecimal.ZERO.setScale(4);
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.createdAt;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Returns false (and changes nothing) if the balance is too low. */
    public boolean tryDebit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            return false;
        }
        balance = balance.subtract(amount);
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
