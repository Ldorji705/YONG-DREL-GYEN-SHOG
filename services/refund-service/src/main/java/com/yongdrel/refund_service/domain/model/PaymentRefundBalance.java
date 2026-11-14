package com.yongdrel.refund_service.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * How much of one payment has been reserved for refunds.
 * Always read with a row lock before reserving, so concurrent refunds are serialized.
 */
@Entity
@Table(name = "payment_refund_balances")
public class PaymentRefundBalance {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payment_id", length = 16, nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "paid_amount", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal paidAmount;

    @Column(name = "reserved_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal reservedAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentRefundBalance() {
    }

    public PaymentRefundBalance(UUID paymentId, BigDecimal paidAmount, String currency) {
        this.paymentId = paymentId;
        this.paidAmount = paidAmount.setScale(4, RoundingMode.HALF_UP);
        this.reservedAmount = BigDecimal.ZERO.setScale(4);
        this.currency = currency;
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public BigDecimal remaining() {
        return paidAmount.subtract(reservedAmount);
    }

    /** Reserve part of the payment; false if it would exceed what was paid. */
    public boolean tryReserve(BigDecimal amount) {
        if (amount.compareTo(remaining()) > 0) {
            return false;
        }
        reservedAmount = reservedAmount.add(amount);
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    public UUID getPaymentId() { return paymentId; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getReservedAmount() { return reservedAmount; }
    public String getCurrency() { return currency; }
}
