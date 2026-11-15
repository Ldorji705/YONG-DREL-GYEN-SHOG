package com.yongdrel.auditlogservice.application.service;

import com.yongdrel.auditlogservice.application.exception.DependencyUnavailableException;
import com.yongdrel.auditlogservice.application.exception.DuplicateAuditEventException;
import com.yongdrel.auditlogservice.application.exception.ForbiddenOperationException;
import com.yongdrel.auditlogservice.application.exception.ReferenceValidationException;
import com.yongdrel.auditlogservice.application.port.out.AuditEventRepositoryPort;
import com.yongdrel.auditlogservice.application.port.out.SourceServiceRegistryPort;
import com.yongdrel.auditlogservice.application.port.out.TenantReferencePort;
import com.yongdrel.auditlogservice.domain.exception.InvalidAuditEventException;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.yongdrel.auditlogservice.TestData.ADMIN;
import static com.yongdrel.auditlogservice.TestData.EVENT_ID;
import static com.yongdrel.auditlogservice.TestData.NOW;
import static com.yongdrel.auditlogservice.TestData.ORGANIZER;
import static com.yongdrel.auditlogservice.TestData.SERVICE;
import static com.yongdrel.auditlogservice.TestData.STUDENT;
import static com.yongdrel.auditlogservice.TestData.draft;
import static com.yongdrel.auditlogservice.TestData.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRecordingServiceTest {

    @Mock AuditEventRepositoryPort events;
    @Mock SourceServiceRegistryPort sources;
    @Mock TenantReferencePort tenants;

    private AuditRecordingService service;

    @BeforeEach
    void setUp() {
        service = new AuditRecordingService(events, sources, tenants, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(sources.isKnown("payment-proof-service")).thenReturn(true);
        lenient().when(tenants.isEnabled()).thenReturn(false);
        lenient().when(events.findByEventId(any())).thenReturn(Optional.empty());
        lenient().when(events.append(any())).thenAnswer(inv -> ((AuditEvent) inv.getArgument(0)).withId(1L));
    }

    @Test
    void serviceRecordsSealedEvent() {
        AuditEvent saved = service.record(draft(), SERVICE);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).append(captor.capture());
        assertThat(captor.getValue().recordedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().isIntact()).isTrue();
        assertThat(saved.id()).isEqualTo(1L);
    }

    @Test
    void adminMayRecord() {
        assertThat(service.record(draft(), ADMIN).id()).isEqualTo(1L);
    }

    @Test
    void usersCannotWriteAuditEvents() {
        assertThatThrownBy(() -> service.record(draft(), STUDENT)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.record(draft(), ORGANIZER)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(events);
    }

    @Test
    void duplicateEventIdIsConflict() {
        when(events.findByEventId(EVENT_ID)).thenReturn(Optional.of(event(7L)));

        assertThatThrownBy(() -> service.record(draft(), SERVICE))
                .isInstanceOf(DuplicateAuditEventException.class)
                .hasMessageContaining("id 7");
        verify(events, never()).append(any());
    }

    @Test
    void invalidDraftIsRejectedBeforeStorage() {
        AuditEventDraft d = draft();
        AuditEventDraft bad = new AuditEventDraft(d.eventId(), d.tenantId(), d.actorType(), d.actorId(), d.actorRole(),
                "bad action", d.outcome(), d.targetType(), d.targetId(), d.sourceService(), d.correlationId(),
                d.occurredAt(), d.detailsJson());
        assertThatThrownBy(() -> service.record(bad, SERVICE)).isInstanceOf(InvalidAuditEventException.class);
        verifyNoInteractions(events);
    }

    @Test
    void unknownSourceServiceIsRejected() {
        when(sources.isKnown("payment-proof-service")).thenReturn(false);

        assertThatThrownBy(() -> service.record(draft(), SERVICE))
                .isInstanceOf(ReferenceValidationException.class)
                .hasMessageContaining("payment-proof-service");
        verify(events, never()).append(any());
    }

    @Test
    void unknownTenantIsRejectedWhenCheckEnabled() {
        when(tenants.isEnabled()).thenReturn(true);
        when(tenants.tenantExists(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.record(draft(), SERVICE))
                .isInstanceOf(ReferenceValidationException.class)
                .hasMessageContaining("Tenant 10");
    }

    @Test
    void knownTenantIsAccepted() {
        when(tenants.isEnabled()).thenReturn(true);
        when(tenants.tenantExists(10L)).thenReturn(true);

        assertThat(service.record(draft(), SERVICE).id()).isEqualTo(1L);
    }

    @Test
    void tenantServiceOutagePropagates() {
        when(tenants.isEnabled()).thenReturn(true);
        when(tenants.tenantExists(10L)).thenThrow(new DependencyUnavailableException("down"));

        assertThatThrownBy(() -> service.record(draft(), SERVICE)).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void brokerPathRecordsWithoutCaller() {
        assertThat(service.recordFromBroker(draft())).isPresent();
    }

    @Test
    void brokerRedeliveryIsIgnored() {
        when(events.findByEventId(EVENT_ID)).thenReturn(Optional.of(event(7L)));

        assertThat(service.recordFromBroker(draft())).isEmpty();
        verify(events, never()).append(any());
    }

    @Test
    void brokerPathStillChecksReferences() {
        when(sources.isKnown("payment-proof-service")).thenReturn(false);
        assertThatThrownBy(() -> service.recordFromBroker(draft())).isInstanceOf(ReferenceValidationException.class);
    }
}
