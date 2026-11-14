
package com.yongdrel.ticket_issuance_service.integration;

import com.yongdrel.ticket_issuance_service.messaging.RabbitMQConfig;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxDispatcher;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventEntity;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventRepository;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.outbox.dispatcher.enabled=false",
        "app.payment.listener.enabled=false"
})
class OutboxDispatcherIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldPublishOutboxEventAndPreventDuplicateDispatch() {

        // Create unique exchange and queue for this test
        String exchangeName =
                "test.ticket.exchange." + UUID.randomUUID();

        String queueName =
                "test.ticket.outbox." + UUID.randomUUID();

        UUID eventId = null;

        String payload =
                "{\"testId\":\"" + UUID.randomUUID() + "\"}";

        try {
            // Create an isolated temporary exchange
            DirectExchange testExchange =
                    new DirectExchange(exchangeName, false, true);

            amqpAdmin.declareExchange(testExchange);

            // Create an isolated temporary queue
            Queue queue = new Queue(
                    queueName,
                    false,
                    false,
                    false
            );

            amqpAdmin.declareQueue(queue);

            // Bind the test queue to the temporary exchange
            amqpAdmin.declareBinding(
                    BindingBuilder.bind(queue)
                            .to(testExchange)
                            .with(RabbitMQConfig.TICKET_ISSUED_ROUTING_KEY)
            );

            // Save a committed outbox event
            OutboxEventEntity event = repository.saveAndFlush(
                    new OutboxEventEntity("TICKET_ISSUED", payload)
            );

            eventId = event.getId();

            // Explicitly enable the dispatcher for this test
            // and provide the temporary exchange
            OutboxDispatcher dispatcher = new OutboxDispatcher(
                    repository,
                    rabbitTemplate,
                    true,
                    exchangeName
            );

            // Run dispatch inside a database transaction
            transactionTemplate.executeWithoutResult(status ->
                    dispatcher.dispatch()
            );

            // Verify the JSON message reached the test queue
            Message received = rabbitTemplate.receive(
                    queueName,
                    5000
            );

            assertNotNull(
                    received,
                    "RabbitMQ should receive the event"
            );

            assertEquals(
                    payload,
                    new String(
                            received.getBody(),
                            StandardCharsets.UTF_8
                    )
            );

            assertEquals(
                    MessageProperties.CONTENT_TYPE_JSON,
                    received.getMessageProperties().getContentType()
            );

            // Verify MySQL published status
            OutboxEventEntity saved = repository.findById(eventId)
                    .orElseThrow();

            assertTrue(
                    saved.isPublished(),
                    "Outbox event should be marked as published"
            );

            // Dispatch again
            transactionTemplate.executeWithoutResult(status ->
                    dispatcher.dispatch()
            );

            // Verify the same event is not published twice
            assertNull(
                    rabbitTemplate.receive(queueName, 1000),
                    "The same event must not be published twice"
            );

        } finally {

            // Clean up the test database record
            if (eventId != null) {
                repository.deleteById(eventId);
            }

            // Clean up the temporary RabbitMQ resources
            amqpAdmin.deleteQueue(queueName);
            amqpAdmin.deleteExchange(exchangeName);
        }
    }
}
