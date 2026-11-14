package com.yongdrel.notification.adapter.out.persistence;

import com.yongdrel.notification.domain.model.Notification;
import com.yongdrel.notification.domain.model.NotificationChannel;
import com.yongdrel.notification.domain.model.NotificationStatus;
import com.yongdrel.notification.domain.model.NotificationTemplate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_event_template_channel",
                        columnNames = {"event_id", "template", "channel"}
                )
        }
)
public class NotificationEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", length = 16, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "recipient_id", columnDefinition = "BINARY(16)", length = 16, nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template", length = 80, nullable = false, updatable = false)
    private NotificationTemplate template;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "event_id", length = 16, nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20, nullable = false, updatable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private NotificationStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(Notification notification) {
        this.id = notification.getId();
        this.recipientId = notification.getRecipientId();
        this.template = notification.getTemplate();
        this.eventId = notification.getEventId();
        this.channel = notification.getChannel();
        this.status = notification.getStatus();
        this.attempts = notification.getAttempts();
        this.createdAt = notification.getCreatedAt();
        this.updatedAt = notification.getUpdatedAt();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public NotificationTemplate getTemplate() {
        return template;
    }

    public UUID getEventId() {
        return eventId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void recordAttempt() {
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
