
package com.yongdrel.ticket_issuance_service.messaging.outbox;

import com.yongdrel.ticket_issuance_service.messaging.RabbitMQConfig;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxDispatcher {

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;

    public OutboxDispatcher(
            OutboxEventRepository repository,
            RabbitTemplate rabbitTemplate,
            @Value("${app.outbox.dispatcher.enabled:false}")
            boolean enabled) {

        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void dispatch() {

        if (!enabled) {
            return;
        }

        var events =
                repository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        for (var event : events) {

            if (!"TICKET_ISSUED".equals(event.getEventType())) {
                continue;
            }

            try {
                CorrelationData correlation =
                        new CorrelationData(event.getId().toString());

                // Prepare the JSON message
                MessageProperties properties =
                        new MessageProperties();

                properties.setContentType(
                        MessageProperties.CONTENT_TYPE_JSON
                );

                properties.setMessageId(
                        event.getId().toString()
                );

                properties.setDeliveryMode(
                        MessageDeliveryMode.PERSISTENT
                );

                // Preserve the original JSON payload
                Message message = new Message(
                        event.getPayload().getBytes(
                                StandardCharsets.UTF_8
                        ),
                        properties
                );

                // Publish to RabbitMQ
                rabbitTemplate.send(
                        RabbitMQConfig.TICKET_EXCHANGE,
                        RabbitMQConfig.TICKET_ISSUED_ROUTING_KEY,
                        message,
                        correlation
                );

                // Wait for broker confirmation
                var confirmation = correlation.getFuture()
                        .get(10, TimeUnit.SECONDS);

                if (!confirmation.isAck()) {
                    throw new IllegalStateException(
                            "RabbitMQ rejected event: "
                                    + confirmation.getReason()
                    );
                }

                // Ensure message was routed to a queue
                if (correlation.getReturned() != null) {
                    throw new IllegalStateException(
                            "RabbitMQ returned unroutable event"
                    );
                }

                // Mark event as published
                event.markPublished();
                repository.save(event);

            } catch (Exception ex) {

                System.err.println(
                        "Outbox publish failed for "
                                + event.getId()
                                + ": "
                                + ex.getMessage()
                );
            }
        }
    }
}
