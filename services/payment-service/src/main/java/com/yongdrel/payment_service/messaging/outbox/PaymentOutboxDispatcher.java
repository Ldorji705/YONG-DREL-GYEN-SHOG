package com.yongdrel.payment_service.messaging.outbox;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxEntity;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxRepository;
import com.yongdrel.payment_service.messaging.RabbitMQConfig;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentOutboxDispatcher {

    private final PaymentOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;

    public PaymentOutboxDispatcher(
            PaymentOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            @Value("${app.outbox.dispatcher.enabled:false}")
            boolean enabled
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.outbox.dispatcher.delay-ms:5000}")
    public void dispatch() {

        if (!enabled) {
            return;
        }

        List<PaymentOutboxEntity> events =
                repository.findTop20ByPublishedFalseOrderByCreatedAtAsc();

        for (PaymentOutboxEntity event : events) {

            try {
                publish(event);
            } catch (Exception exception) {
                System.err.println(
                        "Outbox publication failed for event "
                                + event.getId()
                                + ": "
                                + exception.getMessage()
                );
            }
        }
    }

    private void publish(PaymentOutboxEntity event)
            throws Exception {

        if (!RabbitMQConfig.PAYMENT_CONFIRMED_KEY.equals(
                event.getEventType())) {
            throw new IllegalStateException(
                    "Unsupported outbox event type"
            );
        }

        Message message = MessageBuilder
                .withBody(
                        event.getPayload().getBytes(
                                StandardCharsets.UTF_8
                        )
                )
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.getId().toString())
                .build();

        CorrelationData correlationData =
                new CorrelationData(event.getId().toString());

        rabbitTemplate.send(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_CONFIRMED_KEY,
                message,
                correlationData
        );

        CorrelationData.Confirm confirm =
                correlationData.getFuture().get(
                        10,
                        TimeUnit.SECONDS
                );

        if (!confirm.isAck()) {
            throw new IllegalStateException(
                    "RabbitMQ did not acknowledge message"
            );
        }

        ReturnedMessage returned =
                correlationData.getReturned();

        if (returned != null) {
            throw new IllegalStateException(
                    "RabbitMQ returned unroutable message"
            );
        }

        event.markPublished();
        repository.saveAndFlush(event);

        System.out.println(
                "Published payment event: " + event.getId()
        );
    }
}
