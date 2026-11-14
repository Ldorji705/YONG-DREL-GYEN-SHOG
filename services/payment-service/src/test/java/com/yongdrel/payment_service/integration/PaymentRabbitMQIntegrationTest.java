package com.yongdrel.payment_service.integration;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRabbitMQIntegrationTest {

    @Test
    void shouldPublishAndReceiveMessageUsingIsolatedQueue()
            throws Exception {

        String exchange = "yongdrel.payment.exchange";
        String routingKey = "payment.integration-test."
                + UUID.randomUUID();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername(
                System.getenv().getOrDefault(
                        "RABBITMQ_USER", "yongdrel"
                )
        );
        factory.setPassword(
                System.getenv("RABBITMQ_PASSWORD")
        );

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            String queue = channel.queueDeclare(
                    "",
                    false,
                    true,
                    true,
                    null
            ).getQueue();

            channel.queueBind(queue, exchange, routingKey);

            CachingConnectionFactory springFactory =
                    new CachingConnectionFactory(
                            "localhost", 5672
                    );

            springFactory.setUsername(
                    System.getenv().getOrDefault(
                            "RABBITMQ_USER", "yongdrel"
                    )
            );
            springFactory.setPassword(
                    System.getenv("RABBITMQ_PASSWORD")
            );

            springFactory.setPublisherConfirmType(
                    CachingConnectionFactory.ConfirmType.CORRELATED
            );

            springFactory.setPublisherReturns(true);

            try {
                RabbitTemplate template =
                        new RabbitTemplate(springFactory);

                template.setMandatory(true);

                String payload = "{\"test\":\"payment-rabbitmq\"}";

                Message message = MessageBuilder
                        .withBody(
                                payload.getBytes(StandardCharsets.UTF_8)
                        )
                        .setContentType("application/json")
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .build();

                CorrelationData correlation =
                        new CorrelationData(UUID.randomUUID().toString());

                template.send(
                        exchange,
                        routingKey,
                        message,
                        correlation
                );

                CorrelationData.Confirm confirm =
                        correlation.getFuture().get(
                                10, TimeUnit.SECONDS
                        );

                assertTrue(confirm.isAck());
                assertNull(correlation.getReturned());

                var delivery = channel.basicGet(queue, true);

                assertNotNull(delivery);

                assertEquals(
                        payload,
                        new String(
                                delivery.getBody(),
                                StandardCharsets.UTF_8
                        )
                );

            } finally {
                springFactory.destroy();
            }
        }
    }
}
