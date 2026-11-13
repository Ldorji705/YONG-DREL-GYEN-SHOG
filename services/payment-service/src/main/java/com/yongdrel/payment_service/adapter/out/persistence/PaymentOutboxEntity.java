package com.yongdrel.payment_service.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_outbox")
public class PaymentOutboxEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID paymentId;

    @Column(nullable = false)
    private String eventType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    protected PaymentOutboxEntity() {}

    public PaymentOutboxEntity(
            UUID paymentId,
            String eventType,
            String payload
    ) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.payload = payload;
        this.published = false;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPublished() {
        return published;
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }
}
