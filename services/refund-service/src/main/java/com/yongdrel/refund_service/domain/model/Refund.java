package com.yongdrel.refund_service.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", length = 16, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payment_id", length = 16, nullable = false, updatable = false)
    private UUID paymentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "order_id", length = 16, nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "user_id", length = 100, nullable = false, updatable = false)
    private String userId;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(name = "reason", length = 255, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private RefundStatus status;

    @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version; // null = new row, so Spring Data inserts instead of merging

    protected Refund() {
    }

    public Refund(UUID paymentId, UUID orderId, String userId, BigDecimal amount,
                  String currency, String reason, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount.setScale(4, RoundingMode.UNNECESSARY);
        this.currency = currency;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.status = RefundStatus.PENDING;
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    /** Legal transitions: PENDING/FAILED -> COMPLETED. */
    public void markCompleted(String providerReference) {
        requireStatus(RefundStatus.PENDING, RefundStatus.FAILED);
        this.status = RefundStatus.COMPLETED;
        this.providerReference = providerReference;
        this.failureReason = null;
        this.updatedAt = now();
    }

    /** Legal transitions: PENDING/FAILED -> FAILED (reason kept for retry). */
    public void markFailed(String failureReason) {
        requireStatus(RefundStatus.PENDING, RefundStatus.FAILED);
        this.status = RefundStatus.FAILED;
        this.failureReason = truncate(failureReason);
        this.updatedAt = now();
    }

    public void recordAttempt() {
        this.attempts++;
        this.updatedAt = now();
    }

    private void requireStatus(RefundStatus... allowed) {
        for (RefundStatus s : allowed) {
            if (this.status == s) {
                return;
            }
        }
        throw new IllegalStateException("Refund " + id + " cannot change from " + status);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 255 ? s : s.substring(0, 255);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getProviderReference() { return providerReference; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
