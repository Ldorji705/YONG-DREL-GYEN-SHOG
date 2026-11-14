package com.yongdrel.notification.adapter.out.persistence;

import com.yongdrel.notification.domain.model.Notification;

public class NotificationPersistenceMapper {

    public Notification toDomain(NotificationEntity entity) {
        if (entity == null) {
            return null;
        }

        return new Notification(
                entity.getId(),
                entity.getRecipientId(),
                entity.getTemplate(),
                entity.getEventId(),
                entity.getChannel(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public NotificationEntity toEntity(Notification notification) {
        if (notification == null) {
            return null;
        }

        return new NotificationEntity(notification);
    }
}
