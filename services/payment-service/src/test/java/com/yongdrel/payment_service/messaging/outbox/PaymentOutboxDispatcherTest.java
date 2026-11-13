package com.yongdrel.payment_service.messaging.outbox;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxEntity;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxRepository;
import com.yongdrel.payment_service.messaging.RabbitMQConfig;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentOutboxDispatcherTest {

    private final PaymentOutboxRepository repository =
            mock(PaymentOutboxRepository.class);

    private final RabbitTemplate rabbitTemplate =
            mock(RabbitTemplate.class);

    private PaymentOutboxEntity createEvent() {
        return new PaymentOutboxEntity(
                UUID.randomUUID(),
                "payment.confirmed",
                "{\"paymentId\":\"test\"}"
        );
    }

    @Test
    void shouldNotPublishWhenDisabled() {

        PaymentOutboxDispatcher dispatcher =
                new PaymentOutboxDispatcher(
                        repository, rabbitTemplate, false
                );

        dispatcher.dispatch();

        verifyNoInteractions(repository, rabbitTemplate);
    }

    @Test
    void shouldDoNothingWhenOutboxIsEmpty() {

        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of());

        PaymentOutboxDispatcher dispatcher =
                new PaymentOutboxDispatcher(
                        repository, rabbitTemplate, true
                );

        dispatcher.dispatch();

        verifyNoInteractions(rabbitTemplate);
        verify(repository, never())
                .saveAndFlush(any(PaymentOutboxEntity.class));
    }

    @Test
    void shouldMarkPublishedAfterRabbitMqAck() {

        PaymentOutboxEntity event = createEvent();

        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        doAnswer(invocation -> {
            CorrelationData correlation =
                    invocation.getArgument(3);

            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );

            return null;
        }).when(rabbitTemplate).send(
                eq(RabbitMQConfig.PAYMENT_EXCHANGE),
                eq(RabbitMQConfig.PAYMENT_CONFIRMED_KEY),
                any(Message.class),
                any(CorrelationData.class)
        );

        PaymentOutboxDispatcher dispatcher =
                new PaymentOutboxDispatcher(
                        repository, rabbitTemplate, true
                );

        dispatcher.dispatch();

        assertTrue(event.isPublished());

        verify(repository).saveAndFlush(event);
    }

    @Test
    void shouldNotMarkPublishedWhenRabbitMqNacks() {

        PaymentOutboxEntity event = createEvent();

        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        doAnswer(invocation -> {
            CorrelationData correlation =
                    invocation.getArgument(3);

            correlation.getFuture().complete(
                    new CorrelationData.Confirm(
                            false, "Broker rejected message"
                    )
            );

            return null;
        }).when(rabbitTemplate).send(
                anyString(),
                anyString(),
                any(Message.class),
                any(CorrelationData.class)
        );

        PaymentOutboxDispatcher dispatcher =
                new PaymentOutboxDispatcher(
                        repository, rabbitTemplate, true
                );

        dispatcher.dispatch();

        assertFalse(event.isPublished());

        verify(repository, never())
                .saveAndFlush(any(PaymentOutboxEntity.class));
    }

    @Test
    void shouldNotMarkPublishedWhenMessageIsReturned() {

        PaymentOutboxEntity event = createEvent();

        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        doAnswer(invocation -> {
            CorrelationData correlation =
                    invocation.getArgument(3);

            Message returnedMessage =
                    invocation.getArgument(2);

            correlation.setReturned(
                    new ReturnedMessage(
                            returnedMessage,
                            312,
                            "NO_ROUTE",
                            RabbitMQConfig.PAYMENT_EXCHANGE,
                            RabbitMQConfig.PAYMENT_CONFIRMED_KEY
                    )
            );

            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );

            return null;
        }).when(rabbitTemplate).send(
                anyString(),
                anyString(),
                any(Message.class),
                any(CorrelationData.class)
        );

        PaymentOutboxDispatcher dispatcher =
                new PaymentOutboxDispatcher(
                        repository, rabbitTemplate, true
                );

        dispatcher.dispatch();

        assertFalse(event.isPublished());

        verify(repository, never())
                .saveAndFlush(any(PaymentOutboxEntity.class));
    }

    @Test
    void shouldRejectUnsupportedEventType() {

        PaymentOutboxEntity event = new PaymentOutboxEntity(
                UUID.randomUUID(),
                "payment.refunded",
                "{}"
        );

        when(repository.findTop20ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));

        PaymentOutboxDispatcher dispatcher =
                new PaymentOutboxDispatcher(
                        repository, rabbitTemplate, true
                );

        dispatcher.dispatch();

        assertFalse(event.isPublished());

        verifyNoInteractions(rabbitTemplate);
    }
}
