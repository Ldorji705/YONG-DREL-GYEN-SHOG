package com.yongdrel.notification.adapter.out.persistence;

import com.yongdrel.notification.domain.model.NotificationChannel;
import com.yongdrel.notification.domain.model.NotificationTemplate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
        extends Repository<NotificationEntity, UUID> {

    NotificationEntity save(NotificationEntity notification);

    Optional<NotificationEntity> findByEventIdAndTemplateAndChannel(
            UUID eventId,
            NotificationTemplate template,
            NotificationChannel channel
    );

    Page<NotificationEntity> findAllByRecipientIdOrderByCreatedAtDesc(
            UUID recipientId,
            Pageable pageable
    );

    Optional<NotificationEntity> findByIdAndRecipientId(
            UUID id,
            UUID recipientId
    );
}
