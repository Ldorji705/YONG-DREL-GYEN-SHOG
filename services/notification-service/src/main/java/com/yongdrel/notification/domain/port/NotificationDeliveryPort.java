package com.yongdrel.notification.domain.port;

import com.yongdrel.notification.domain.model.NotificationChannel;

import java.util.UUID;


public interface NotificationDeliveryPort {

    void deliver(
            UUID recipientId,
            NotificationChannel channel,
            String message
    );
}
