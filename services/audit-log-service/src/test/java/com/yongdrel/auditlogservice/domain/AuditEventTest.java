package com.yongdrel.auditlogservice.domain;

import com.yongdrel.auditlogservice.domain.exception.InvalidAuditEventException;
import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.Role;
import com.yongdrel.auditlogservice.domain.model.TargetType;
import com.yongdrel.auditlogservice.domain.service.IntegrityHasher;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.yongdrel.auditlogservice.TestData.NOW;
import static com.yongdrel.auditlogservice.TestData.draft;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventTest {

    private static AuditEventDraft with(java.util.function.UnaryOperator<Builder> change) {
        return change.apply(new Builder(draft())).build();
    }

    @Test
    void createStampsRecordedAtAndSeals() {
        AuditEvent e = AuditEvent.create(draft(), NOW);

        assertThat(e.id()).isNull();
        assertThat(e.recordedAt()).isEqualTo(NOW);
        assertThat(e.integrityHash()).hasSize(64).isEqualTo(IntegrityHasher.hash(e));
        assertThat(e.isIntact()).isTrue();
        assertThat(e.withId(9L).isIntact()).as("id is not part of the hash").isTrue();
    }

    @Nested
    class Integrity {

        @Test
        void anyChangedFieldBreaksTheSeal() {
            AuditEvent e = AuditEvent.create(draft(), NOW);
            AuditEvent tampered = new AuditEvent(e.id(), e.eventId(), e.tenantId(), e.actorType(), e.actorId(),
                    e.actorRole(), "PAYMENT_VERIFIED", e.outcome(), e.targetType(), e.targetId(), e.sourceService(),
                    e.correlationId(), e.occurredAt(), e.recordedAt(), e.detailsJson(), e.integrityHash());

            assertThat(tampered.isIntact()).isFalse();
        }

        @Test
        void tamperedDetailsBreakTheSeal() {
            AuditEvent e = AuditEvent.create(draft(), NOW);
            AuditEvent tampered = new AuditEvent(e.id(), e.eventId(), e.tenantId(), e.actorType(), e.actorId(),
                    e.actorRole(), e.action(), e.outcome(), e.targetType(), e.targetId(), e.sourceService(),
                    e.correlationId(), e.occurredAt(), e.recordedAt(), "{\"amount\":1}", e.integrityHash());

            assertThat(tampered.isIntact()).isFalse();
        }

        @Test
        void missingHashIsNotIntact() {
            AuditEvent e = AuditEvent.create(draft(), NOW);
            AuditEvent noHash = new AuditEvent(1L, e.eventId(), e.tenantId(), e.actorType(), e.actorId(),
                    e.actorRole(), e.action(), e.outcome(), e.targetType(), e.targetId(), e.sourceService(),
                    e.correlationId(), e.occurredAt(), e.recordedAt(), e.detailsJson(), null);
            assertThat(noHash.isIntact()).isFalse();
        }

        @Test
        void nanosecondsBeyondDatabasePrecisionDoNotBreakTheSeal() {
            AuditEvent e = AuditEvent.create(draft(), NOW.plusNanos(123_456_789));
            AuditEvent asStored = new AuditEvent(e.id(), e.eventId(), e.tenantId(), e.actorType(), e.actorId(),
                    e.actorRole(), e.action(), e.outcome(), e.targetType(), e.targetId(), e.sourceService(),
                    e.correlationId(), e.occurredAt(), NOW.plusNanos(123_456_000), e.detailsJson(), e.integrityHash());
            assertThat(asStored.isIntact()).isTrue();
        }
    }

    @Nested
    class Validation {

        @Test
        void systemActorWithoutIdIsValid() {
            AuditEvent e = AuditEvent.create(with(b -> b.actor(ActorType.SYSTEM, null, null)), NOW);
            assertThat(e.actorId()).isNull();
        }

        @Test
        void platformEventWithoutTenantIsValid() {
            assertThat(AuditEvent.create(with(b -> b.tenant(null)), NOW).tenantId()).isNull();
        }

        @Test
        void clockSkewUpToFiveMinutesIsAccepted() {
            assertThat(AuditEvent.create(with(b -> b.occurredAt(NOW.plus(Duration.ofMinutes(5)))), NOW)).isNotNull();
        }

        @Test
        void futureEventIsRejected() {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.occurredAt(NOW.plus(Duration.ofMinutes(5)).plusMillis(1))), NOW))
                    .isInstanceOf(InvalidAuditEventException.class).hasMessageContaining("future");
        }

        @Test
        void userActorNeedsId() {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.actor(ActorType.USER, null, Role.STUDENT)), NOW))
                    .hasMessageContaining("actorId is required");
        }

        @Test
        void roleOnlyForUsers() {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.actor(ActorType.SYSTEM, null, Role.ADMIN)), NOW))
                    .hasMessageContaining("actorRole");
        }

        @Test
        void organizerActionNeedsTenant() {
            assertThatThrownBy(() -> AuditEvent.create(
                    with(b -> b.actor(ActorType.USER, 3L, Role.ORGANIZER).tenant(null)), NOW))
                    .hasMessageContaining("tenantId");
        }

        @Test
        void nonPositiveIdsAreRejected() {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.tenant(0L)), NOW)).hasMessageContaining("tenantId");
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.actor(ActorType.USER, -1L, null)), NOW))
                    .hasMessageContaining("actorId");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"payment_verified", "AB", "1ACTION", "PAYMENT-VERIFIED"})
        void badActions(String action) {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.action(action)), NOW))
                    .isInstanceOf(InvalidAuditEventException.class).hasMessageContaining("action");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Order Service", "x", "PaymentService", "9service"})
        void badSourceServices(String source) {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.source(source)), NOW))
                    .hasMessageContaining("sourceService");
        }

        @ParameterizedTest
        @ValueSource(strings = {"short", "has space in it", "semi;colon-1234"})
        void badCorrelationIds(String correlationId) {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.correlation(correlationId)), NOW))
                    .hasMessageContaining("correlationId");
        }

        @Test
        void badTargetId() {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.targetId("a/b")), NOW))
                    .hasMessageContaining("targetId");
        }

        @Test
        void detailsSizeLimit() {
            String big = "{\"x\":\"" + "a".repeat(AuditEvent.MAX_DETAILS_LENGTH) + "\"}";
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.details(big)), NOW))
                    .hasMessageContaining("details");
        }

        @Test
        void requiredFields() {
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.eventId(null)), NOW)).hasMessageContaining("eventId");
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.outcome(null)), NOW)).hasMessageContaining("outcome");
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.targetType(null)), NOW)).hasMessageContaining("targetType");
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.occurredAt(null)), NOW)).hasMessageContaining("occurredAt");
            assertThatThrownBy(() -> AuditEvent.create(with(b -> b.actor(null, 1L, null)), NOW)).hasMessageContaining("actorType");
        }
    }

    /** Tiny mutable builder for test variations. */
    static final class Builder {
        private UUID eventId; private Long tenantId; private ActorType actorType; private Long actorId; private Role role;
        private String action; private Outcome outcome; private TargetType targetType; private String targetId;
        private String source; private String correlation; private Instant occurredAt; private String details;

        Builder(AuditEventDraft d) {
            eventId = d.eventId(); tenantId = d.tenantId(); actorType = d.actorType(); actorId = d.actorId();
            role = d.actorRole(); action = d.action(); outcome = d.outcome(); targetType = d.targetType();
            targetId = d.targetId(); source = d.sourceService(); correlation = d.correlationId();
            occurredAt = d.occurredAt(); details = d.detailsJson();
        }

        Builder eventId(UUID v) { eventId = v; return this; }
        Builder tenant(Long v) { tenantId = v; return this; }
        Builder actor(ActorType t, Long id, Role r) { actorType = t; actorId = id; role = r; return this; }
        Builder action(String v) { action = v; return this; }
        Builder outcome(Outcome v) { outcome = v; return this; }
        Builder targetType(TargetType v) { targetType = v; return this; }
        Builder targetId(String v) { targetId = v; return this; }
        Builder source(String v) { source = v; return this; }
        Builder correlation(String v) { correlation = v; return this; }
        Builder occurredAt(Instant v) { occurredAt = v; return this; }
        Builder details(String v) { details = v; return this; }

        AuditEventDraft build() {
            return new AuditEventDraft(eventId, tenantId, actorType, actorId, role, action, outcome, targetType,
                    targetId, source, correlation, occurredAt, details);
        }
    }
}
