package com.yongdrel.notification.application.service;

import com.yongdrel.notification.application.template.NotificationTemplateRenderer;
import com.yongdrel.notification.domain.model.Notification;
import com.yongdrel.notification.domain.model.NotificationChannel;
import com.yongdrel.notification.domain.model.NotificationStatus;
import com.yongdrel.notification.domain.model.NotificationTemplate;
import com.yongdrel.notification.domain.port.NotificationDeliveryPort;
import com.yongdrel.notification.domain.port.NotificationStore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class NotificationApplicationService {

    private final NotificationStore notificationStore;
    private final NotificationTemplateRenderer templateRenderer;
    private final NotificationDeliveryPort deliveryPort;

    public NotificationApplicationService(
            NotificationStore notificationStore,
            NotificationTemplateRenderer templateRenderer,
            NotificationDeliveryPort deliveryPort
    ) {
        this.notificationStore = notificationStore;
        this.templateRenderer = templateRenderer;
        this.deliveryPort = deliveryPort;
    }

    public Notification createIfNotExists(
            UUID recipientId,
            NotificationTemplate template,
            UUID eventId,
            NotificationChannel channel
    ) {
        Objects.requireNonNull(recipientId, "Recipient ID is required");
        Objects.requireNonNull(template, "Notification template is required");
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(channel, "Notification channel is required");

        return notificationStore
                .findByEventIdAndTemplateAndChannel(eventId, template, channel)
                .orElseGet(() -> {
                    Notification notification = Notification.create(
                            recipientId,
                            template,
                            eventId,
                            channel
                    );

                    return notificationStore.save(notification);
                });
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public Notification processNotification(
            UUID recipientId,
            NotificationTemplate template,
            UUID eventId,
            NotificationChannel channel,
            Map<String, String> variables
    ) {
        Notification notification = createIfNotExists(
                recipientId,
                template,
                eventId,
                channel
        );

        if (notification.getStatus() == NotificationStatus.SENT) {
            return notification;
        }

        notification.recordAttempt();
        notificationStore.save(notification);

        try {
            String message = templateRenderer.render(template, variables);

            deliveryPort.deliver(
                    recipientId,
                    channel,
                    message
            );

            notification.markSent();

            return notificationStore.save(notification);

        } catch (RuntimeException exception) {
            notification.markFailed();
            notificationStore.save(notification);

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotificationsForRecipient(
            UUID recipientId
    ) {
        Objects.requireNonNull(recipientId, "Recipient ID is required");

        return notificationStore.findAllByRecipientId(recipientId);
    }
}
