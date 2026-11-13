package com.yongdrel.payment_service.adapter.out.persistence;

import com.yongdrel.payment_service.domain.model.PaymentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_payment_order",
            columnNames = "order_id"
        )
    }
)
public class PaymentEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "lottery_event_id", nullable = false, updatable = false)
    private UUID lotteryEventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_reference", unique = true)
    private String providerReference;

    @Column(name = "payment_proof_id")
    private UUID paymentProofId;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(
            UUID orderId,
            UUID lotteryEventId,
            Long userId,
            Integer quantity,
            BigDecimal amount,
            String currency
    ) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.lotteryEventId = lotteryEventId;
        this.userId = userId;
        this.quantity = quantity;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getLotteryEventId() {
        return lotteryEventId;
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void markProcessing() {
        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void markConfirmed(String providerReference) {
        this.status = PaymentStatus.CONFIRMED;
        this.providerReference = providerReference;
        this.confirmedAt = LocalDateTime.now();
        this.updatedAt = this.confirmedAt;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getPaymentProofId() {
    return paymentProofId;
}

public Long getVerifiedBy() {
    return verifiedBy;
}

public LocalDateTime getVerifiedAt() {
    return verifiedAt;
}

public String getRejectionReason() {
    return rejectionReason;
}

public void markPendingVerification(UUID proofId) {
    if (status != PaymentStatus.PENDING) {
        throw new IllegalStateException(
                "Payment is not awaiting proof submission"
        );
    }

    if (proofId == null) {
        throw new IllegalArgumentException(
                "Payment proof ID is required"
        );
    }

    this.paymentProofId = proofId;
    this.status = PaymentStatus.PENDING_VERIFICATION;
    this.updatedAt = LocalDateTime.now();
}

public void markApproved(Long organizerId) {
    if (status != PaymentStatus.PENDING_VERIFICATION) {
        throw new IllegalStateException(
                "Payment is not awaiting verification"
        );
    }

    if (organizerId == null) {
        throw new IllegalArgumentException(
                "Organizer ID is required"
        );
    }

    this.verifiedBy = organizerId;
    this.verifiedAt = LocalDateTime.now();
    this.status = PaymentStatus.CONFIRMED;
    this.confirmedAt = this.verifiedAt;
    this.updatedAt = this.verifiedAt;
}

    public void markRejected(Long organizerId, String reason) {
        if (status != PaymentStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException(
                    "Payment is not awaiting verification"
            );
        }

        if (organizerId == null) {
            throw new IllegalArgumentException(
                    "Organizer ID is required"
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Rejection reason is required"
            );
        }

        this.verifiedBy = organizerId;
        this.verifiedAt = LocalDateTime.now();
        this.rejectionReason = reason;
        this.status = PaymentStatus.REJECTED;
        this.updatedAt = this.verifiedAt;
}
}
