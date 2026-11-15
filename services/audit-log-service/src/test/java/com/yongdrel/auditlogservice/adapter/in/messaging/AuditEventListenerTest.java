package com.yongdrel.auditlogservice.adapter.in.messaging;

import com.yongdrel.auditlogservice.adapter.in.web.dto.RecordAuditEventRequest;
import com.yongdrel.auditlogservice.application.exception.ReferenceValidationException;
import com.yongdrel.auditlogservice.application.port.in.RecordAuditEventUseCase;
import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.TargetType;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.yongdrel.auditlogservice.TestData.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Consuming business audit events and fraud flag events from RabbitMQ. */
class AuditEventListenerTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private RecordAuditEventUseCase recorder;
    private AuditEventListener listener;

    @BeforeEach
    void setUp() {
        recorder = mock(RecordAuditEventUseCase.class);
        listener = new AuditEventListener(recorder, validator);
    }

    private static RecordAuditEventRequest message(String action, String correlationId) {
        return new RecordAuditEventRequest(UUID.randomUUID(), 10L, ActorType.SERVICE, null, null, action,
                Outcome.SUCCESS, TargetType.ORDER, "5001", "scheduler-service", correlationId,
                Instant.parse("2026-11-10T09:00:00Z"), Map.of("reason", "payment timeout"));
    }

    @Test
    void validMessageIsRecorded() {
        when(recorder.recordFromBroker(any())).thenReturn(Optional.of(event(1L)));

        listener.onAuditEvent(message("ORDER_CANCELLED_UNPAID", "order-5001-checkout"));

        ArgumentCaptor<AuditEventDraft> draft = ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(recorder).recordFromBroker(draft.capture());
        assertThat(draft.getValue().action()).isEqualTo("ORDER_CANCELLED_UNPAID");
        assertThat(draft.getValue().detailsJson()).isEqualTo("{\"reason\":\"payment timeout\"}");
    }

    @Test
    void missingCorrelationIdDefaultsToEventId() {
        when(recorder.recordFromBroker(any())).thenReturn(Optional.empty());
        RecordAuditEventRequest m = message("ORDER_CANCELLED_UNPAID", null);

        listener.onAuditEvent(m);

        ArgumentCaptor<AuditEventDraft> draft = ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(recorder).recordFromBroker(draft.capture());
        assertThat(draft.getValue().correlationId()).isEqualTo(m.eventId().toString());
    }

    @Test
    void invalidMessageIsDeadLettered() {
        assertThatThrownBy(() -> listener.onAuditEvent(message("not valid", "order-5001-checkout")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("action");
        verifyNoInteractions(recorder);
    }

    @Test
    void referenceFailureIsDeadLettered() {
        when(recorder.recordFromBroker(any())).thenThrow(new ReferenceValidationException("Unknown sourceService"));

        assertThatThrownBy(() -> listener.onAuditEvent(message("ORDER_CANCELLED_UNPAID", "order-5001-checkout")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void fraudFlagRaisedMapsToSystemAction() {
        FraudFlagEventMessage raised = new FraudFlagEventMessage("FLAG_RAISED", 7L, 2L, 10L, 5L, 5002L, 202L, 100,
                "HIGH", List.of("DUPLICATE_PAYMENT_REFERENCE"), "OPEN", null, Instant.parse("2026-11-10T09:00:00Z"));

        AuditEventDraft d = AuditEventListener.toDraft(raised);

        assertThat(d.action()).isEqualTo("FRAUD_FLAG_RAISED");
        assertThat(d.actorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(d.targetType()).isEqualTo(TargetType.RISK_FLAG);
        assertThat(d.targetId()).isEqualTo("7");
        assertThat(d.correlationId()).isEqualTo("fraud-flag-7");
        assertThat(d.sourceService()).isEqualTo("fraud-risk-service");
        assertThat(d.detailsJson()).contains("\"orderId\":5002").contains("DUPLICATE_PAYMENT_REFERENCE");
        assertThat(d.eventId()).as("deterministic, so redelivery is idempotent")
                .isEqualTo(AuditEventListener.toDraft(raised).eventId());
    }

    @Test
    void fraudFlagReviewedRecordsTheReviewer() {
        FraudFlagEventMessage reviewed = new FraudFlagEventMessage("FLAG_REVIEWED", 7L, 2L, 10L, 5L, 5002L, 202L,
                100, "HIGH", List.of(), "CONFIRMED_FRAUD", 3L, Instant.parse("2026-11-10T09:05:00Z"));

        AuditEventDraft d = AuditEventListener.toDraft(reviewed);

        assertThat(d.action()).isEqualTo("FRAUD_FLAG_REVIEWED");
        assertThat(d.actorType()).isEqualTo(ActorType.USER);
        assertThat(d.actorId()).isEqualTo(3L);
    }

    @Test
    void fraudEventsAreRecordedAndIncompleteOnesRejected() {
        when(recorder.recordFromBroker(any())).thenReturn(Optional.of(event(1L)));
        listener.onFraudFlagEvent(new FraudFlagEventMessage("FLAG_RAISED", 7L, 2L, 10L, 5L, 5002L, 202L, 100,
                "HIGH", List.of(), "OPEN", null, Instant.parse("2026-11-10T09:00:00Z")));
        verify(recorder).recordFromBroker(any());

        assertThatThrownBy(() -> listener.onFraudFlagEvent(new FraudFlagEventMessage("FLAG_RAISED", null, null, 10L,
                null, null, null, null, null, null, null, null, Instant.now())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
