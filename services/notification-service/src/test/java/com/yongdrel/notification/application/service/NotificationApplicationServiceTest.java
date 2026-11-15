
package com.yongdrel.notification.application.service;

import com.yongdrel.notification.application.template.NotificationTemplateRenderer;
import com.yongdrel.notification.domain.model.Notification;
import com.yongdrel.notification.domain.model.NotificationChannel;
import com.yongdrel.notification.domain.model.NotificationStatus;
import com.yongdrel.notification.domain.model.NotificationTemplate;
import com.yongdrel.notification.domain.port.NotificationDeliveryPort;
import com.yongdrel.notification.domain.port.NotificationStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    @Mock
    private NotificationStore notificationStore;

    @Mock
    private NotificationTemplateRenderer templateRenderer;

    @Mock
    private NotificationDeliveryPort deliveryPort;

    @InjectMocks
    private NotificationApplicationService service;

    private UUID recipientId;
    private UUID eventId;

    private final NotificationTemplate template =
            NotificationTemplate.TICKET_PURCHASED;

    private final NotificationChannel channel =
            NotificationChannel.EMAIL;

    @BeforeEach
    void setUp() {
        recipientId = UUID.randomUUID();
        eventId = UUID.randomUUID();
    }

    @Test
    void shouldCreateNewNotificationWhenEventDoesNotExist() {
        when(notificationStore.findByEventIdAndTemplateAndChannel(
                eventId, template, channel
        )).thenReturn(Optional.empty());

        when(notificationStore.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = service.createIfNotExists(
                recipientId, template, eventId, channel
        );

        assertNotNull(result);
        assertEquals(recipientId, result.getRecipientId());
        assertEquals(eventId, result.getEventId());
        assertEquals(NotificationStatus.PENDING, result.getStatus());

        verify(notificationStore).save(any(Notification.class));
    }

    @Test
    void shouldReturnExistingNotificationForDuplicateEvent() {
        Notification existing = Notification.create(
                recipientId, template, eventId, channel
        );

        when(notificationStore.findByEventIdAndTemplateAndChannel(
                eventId, template, channel
        )).thenReturn(Optional.of(existing));

        Notification result = service.createIfNotExists(
                recipientId, template, eventId, channel
        );

        assertSame(existing, result);

        verify(notificationStore, never()).save(any(Notification.class));
    }

    @Test
    void shouldMarkNotificationAsSentAfterSuccessfulDelivery() {
        when(notificationStore.findByEventIdAndTemplateAndChannel(
                eventId, template, channel
        )).thenReturn(Optional.empty());

        when(notificationStore.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(templateRenderer.render(eq(template), anyMap()))
                .thenReturn("Your ticket has been issued.");

        Notification result = service.processNotification(
                recipientId,
                template,
                eventId,
                channel,
                Map.of("eventName", "Test Event", "orderId", "ORDER-1")
        );

        assertEquals(NotificationStatus.SENT, result.getStatus());
        assertEquals(1, result.getAttempts());

        verify(deliveryPort).deliver(
                recipientId,
                channel,
                "Your ticket has been issued."
        );
    }

    @Test
    void shouldMarkNotificationAsFailedWhenDeliveryFails() {
        when(notificationStore.findByEventIdAndTemplateAndChannel(
                eventId, template, channel
        )).thenReturn(Optional.empty());

        when(notificationStore.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(templateRenderer.render(eq(template), anyMap()))
                .thenReturn("Your ticket has been issued.");

        doThrow(new RuntimeException("Delivery failed"))
                .when(deliveryPort)
                .deliver(eq(recipientId), eq(channel), anyString());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.processNotification(
                        recipientId,
                        template,
                        eventId,
                        channel,
                        Map.of("eventName", "Test Event", "orderId", "ORDER-1")
                )
        );

        assertEquals("Delivery failed", exception.getMessage());

        verify(notificationStore, times(3))
                .save(any(Notification.class));
    }

    @Test
    void shouldNotDeliverNotificationAgainWhenAlreadySent() {
        Notification existing = Notification.create(
                recipientId, template, eventId, channel
        );
        existing.markSent();

        when(notificationStore.findByEventIdAndTemplateAndChannel(
                eventId, template, channel
        )).thenReturn(Optional.of(existing));

        Notification result = service.processNotification(
                recipientId,
                template,
                eventId,
                channel,
                Map.of()
        );

        assertSame(existing, result);
        assertEquals(NotificationStatus.SENT, result.getStatus());

        verify(deliveryPort, never())
                .deliver(any(), any(), anyString());

        verify(templateRenderer, never())
                .render(any(), anyMap());
    }

    @Test
    void shouldReturnNotificationsForRequestedRecipient() {
        Notification notification = Notification.create(
                recipientId, template, eventId, channel
        );

        when(notificationStore.findAllByRecipientId(recipientId))
                .thenReturn(List.of(notification));

        List<Notification> result =
                service.getNotificationsForRecipient(recipientId);

        assertEquals(1, result.size());
        assertEquals(recipientId, result.get(0).getRecipientId());

        verify(notificationStore).findAllByRecipientId(recipientId);
    }
}
