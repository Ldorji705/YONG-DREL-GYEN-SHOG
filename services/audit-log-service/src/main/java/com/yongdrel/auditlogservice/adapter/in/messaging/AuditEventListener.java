package com.yongdrel.auditlogservice.adapter.in.messaging;

import com.yongdrel.auditlogservice.adapter.in.common.DetailsJson;
import com.yongdrel.auditlogservice.adapter.in.web.dto.RecordAuditEventRequest;
import com.yongdrel.auditlogservice.application.port.in.RecordAuditEventUseCase;
import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.TargetType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consumes business audit events. Invalid messages are rejected to the dead-letter queue;
 * redeliveries of an already-recorded eventId are acknowledged and ignored.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final RecordAuditEventUseCase recorder;
    private final Validator validator;

    public AuditEventListener(RecordAuditEventUseCase recorder, Validator validator) {
        this.recorder = recorder;
        this.validator = validator;
    }

    @RabbitListener(queues = RabbitMessagingConfig.AUDIT_QUEUE)
    public void onAuditEvent(RecordAuditEventRequest message) {
        Set<ConstraintViolation<RecordAuditEventRequest>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String reason = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted().collect(Collectors.joining(", "));
            throw new AmqpRejectAndDontRequeueException("Invalid audit event: " + reason);
        }
        String correlationId = message.correlationId() != null ? message.correlationId() : message.eventId().toString();
        record(message.toDraft(correlationId));
    }

    @RabbitListener(queues = RabbitMessagingConfig.FRAUD_QUEUE)
    public void onFraudFlagEvent(FraudFlagEventMessage m) {
        if (m.flagId() == null || m.tenantId() == null || m.eventType() == null || m.occurredAt() == null) {
            throw new AmqpRejectAndDontRequeueException("Fraud flag event is missing flagId/tenantId/eventType/occurredAt");
        }
        record(toDraft(m));
    }

    /** Maps a fraud flag event; the eventId is derived from its content so redeliveries are idempotent. */
    static AuditEventDraft toDraft(FraudFlagEventMessage m) {
        String action = "FRAUD_" + m.eventType().replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
        UUID eventId = UUID.nameUUIDFromBytes(("fraud-risk-service:" + m.eventType() + ":" + m.flagId() + ":"
                + m.status()).getBytes(StandardCharsets.UTF_8));
        boolean reviewed = m.reviewerId() != null;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("checkId", m.checkId());
        details.put("orderId", m.orderId());
        details.put("eventId", m.eventId());
        details.put("buyerId", m.buyerId());
        details.put("score", m.score());
        details.put("riskLevel", m.riskLevel());
        details.put("reasons", m.reasons());
        details.put("status", m.status());
        details.values().removeIf(java.util.Objects::isNull);
        return new AuditEventDraft(eventId, m.tenantId(), reviewed ? ActorType.USER : ActorType.SYSTEM,
                m.reviewerId(), null, action, Outcome.SUCCESS, TargetType.RISK_FLAG, String.valueOf(m.flagId()),
                "fraud-risk-service", "fraud-flag-" + m.flagId(), m.occurredAt(), DetailsJson.write(details));
    }

    private void record(AuditEventDraft draft) {
        try {
            recorder.recordFromBroker(draft).ifPresentOrElse(
                    saved -> log.debug("Recorded audit event {} ({})", saved.id(), saved.action()),
                    () -> log.info("Ignored redelivered audit event {}", draft.eventId()));
        } catch (RuntimeException e) {
            log.warn("Rejecting audit event {}: {}", draft.eventId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
