package com.yongdrel.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Notification {

    private final UUID id;
    private final UUID recipientId;
    private final NotificationTemplate template;
    private final UUID eventId;
    private final NotificationChannel channel;

    private NotificationStatus status;
    private int attempts;
    private final Instant createdAt;
    private Instant updatedAt;

    public Notification(
            UUID id,
            UUID recipientId,
            NotificationTemplate template,
            UUID eventId,
            NotificationChannel channel,
            NotificationStatus status,
            int attempts,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.recipientId = recipientId;
        this.template = template;
        this.eventId = eventId;
        this.channel = channel;
        this.status = status;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Notification create(
            UUID recipientId,
            NotificationTemplate template,
            UUID eventId,
            NotificationChannel channel
    ) {
        Instant now = Instant.now();

        return new Notification(
                UUID.randomUUID(),
                recipientId,
                template,
                eventId,
                channel,
                NotificationStatus.PENDING,
                0,
                now,
                now
        );
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
}
