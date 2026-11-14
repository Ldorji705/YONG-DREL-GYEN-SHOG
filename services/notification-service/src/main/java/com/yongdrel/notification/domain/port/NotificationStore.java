package com.yongdrel.notification.domain.port;

import com.yongdrel.notification.domain.model.Notification;
import com.yongdrel.notification.domain.model.NotificationChannel;
import com.yongdrel.notification.domain.model.NotificationTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationStore {

    Notification save(Notification notification);

    Optional<Notification> findByEventIdAndTemplateAndChannel(
            UUID eventId,
            NotificationTemplate template,
            NotificationChannel channel
    );

    List<Notification> findAllByRecipientId(UUID recipientId);
}
