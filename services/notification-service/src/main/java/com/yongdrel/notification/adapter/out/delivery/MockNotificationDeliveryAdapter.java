package com.yongdrel.notification.adapter.out.delivery;

import com.yongdrel.notification.domain.model.NotificationChannel;
import com.yongdrel.notification.domain.port.NotificationDeliveryPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class MockNotificationDeliveryAdapter
        implements NotificationDeliveryPort {

    private static final Logger log =
            LoggerFactory.getLogger(MockNotificationDeliveryAdapter.class);

    @Override
    public void deliver(
            UUID recipientId,
            NotificationChannel channel,
            String message
    ) {
        Objects.requireNonNull(recipientId, "Recipient ID is required");
        Objects.requireNonNull(channel, "Notification channel is required");

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Notification message cannot be empty"
            );
        }

        switch (channel) {
            case EMAIL ->
                    log.info(
                            "MOCK EMAIL delivered to recipient {}: {}",
                            recipientId,
                            message
                    );

            case SMS ->
                    log.info(
                            "MOCK SMS delivered to recipient {}: {}",
                            recipientId,
                            message
                    );
        }
    }
}
