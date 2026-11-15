package com.yongdrel.notification.adapter.in.messaging;

import com.yongdrel.notification.adapter.in.messaging.event.TicketIssuedEvent;
import com.yongdrel.notification.application.service.NotificationApplicationService;
import com.yongdrel.notification.config.NotificationRabbitMQConfig;
import com.yongdrel.notification.domain.model.NotificationChannel;
import com.yongdrel.notification.domain.model.NotificationTemplate;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
public class TicketIssuedNotificationListener {

    private final NotificationApplicationService notificationService;

    public TicketIssuedNotificationListener(
            NotificationApplicationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @RabbitListener(
            queues = NotificationRabbitMQConfig.NOTIFICATION_QUEUE
    )
    public void handleTicketIssued(TicketIssuedEvent event) {

        Objects.requireNonNull(event, "Ticket-issued event is required");
        Objects.requireNonNull(event.eventId(), "Event ID is required");
        Objects.requireNonNull(event.orderId(), "Order ID is required");
        Objects.requireNonNull(event.userId(), "User ID is required");
        Objects.requireNonNull(
                event.lotteryEventId(),
                "Lottery event ID is required"
        );

        Map<String, String> variables = Map.of(
                "eventName", "Lottery event " + event.lotteryEventId(),
                "orderId", event.orderId().toString()
        );

        notificationService.processNotification(
                event.userId(),
                NotificationTemplate.TICKET_PURCHASED,
                event.eventId(),
                NotificationChannel.EMAIL,
                variables
        );
    }
}
