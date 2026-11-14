package com.yongdrel.notification.adapter.out.persistence;

import com.yongdrel.notification.domain.model.Notification;
import com.yongdrel.notification.domain.model.NotificationChannel;
import com.yongdrel.notification.domain.model.NotificationTemplate;
import com.yongdrel.notification.domain.port.NotificationStore;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
public class NotificationPersistenceAdapter implements NotificationStore {

    private final NotificationRepository repository;
    private final NotificationPersistenceMapper mapper;

    public NotificationPersistenceAdapter(
            NotificationRepository repository
    ) {
        this.repository = repository;
        this.mapper = new NotificationPersistenceMapper();
    }

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity = mapper.toEntity(notification);
        NotificationEntity savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findByEventIdAndTemplateAndChannel(
            UUID eventId,
            NotificationTemplate template,
            NotificationChannel channel
    ) {
        return repository
                .findByEventIdAndTemplateAndChannel(eventId, template, channel)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findAllByRecipientId(UUID recipientId) {
        return repository
                .findAllByRecipientIdOrderByCreatedAtDesc(
                        recipientId,
                        Pageable.unpaged()
                )
                .map(mapper::toDomain)
                .getContent();
    }
}
