package com.yongdrel.payment_service.integration;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxEntity;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxRepository;
import com.yongdrel.payment_service.messaging.RabbitMQConfig;
import com.yongdrel.payment_service.messaging.outbox.PaymentOutboxDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentOutboxRabbitMQIntegrationTest {

    @Test
    void shouldPublishToIsolatedRabbitMQAndUpdateOutbox()
            throws Exception {

        String testExchange =
                "yongdrel.payment.integration." + UUID.randomUUID();

        String routingKey =
                RabbitMQConfig.PAYMENT_CONFIRMED_KEY;

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

            channel.exchangeDeclare(testExchange, "direct", false);

            String queue = channel.queueDeclare(
                    "",
                    false,
                    true,
                    true,
                    null
            ).getQueue();

            channel.queueBind(queue, testExchange, routingKey);

            CachingConnectionFactory springFactory =
                    new CachingConnectionFactory("localhost", 5672);

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
                RabbitTemplate template = new RabbitTemplate(springFactory) {
                    @Override
                    public void send(
                            String exchange,
                            String key,
                            Message message,
                            CorrelationData correlationData
                    ) {
                        super.send(
                                testExchange,
                                key,
                                message,
                                correlationData
                        );
                    }
                };

                template.setMandatory(true);

                PaymentOutboxRepository repository =
                        org.mockito.Mockito.mock(
                                PaymentOutboxRepository.class
                        );

                PaymentOutboxEntity event =
                        new PaymentOutboxEntity(
                                UUID.randomUUID(),
                                routingKey,
                                "{\"test\":\"outbox\"}"
                        );

                org.mockito.Mockito.when(
                        repository.findTop20ByPublishedFalseOrderByCreatedAtAsc()
                ).thenReturn(List.of(event));

                PaymentOutboxDispatcher dispatcher =
                        new PaymentOutboxDispatcher(
                                repository,
                                template,
                                true
                        );

                dispatcher.dispatch();

                assertTrue(event.isPublished());

                org.mockito.Mockito.verify(repository)
                        .saveAndFlush(event);

                var delivery = channel.basicGet(queue, true);

                assertNotNull(delivery);

                assertEquals(
                        "{\"test\":\"outbox\"}",
                        new String(
                                delivery.getBody(),
                                StandardCharsets.UTF_8
                        )
                );

            } finally {
                springFactory.destroy();
            }

            channel.exchangeDelete(testExchange);
        }
    }
}
