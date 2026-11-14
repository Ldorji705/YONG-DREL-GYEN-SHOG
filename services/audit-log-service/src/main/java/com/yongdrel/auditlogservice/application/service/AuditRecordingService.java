package com.yongdrel.auditlogservice.application.service;

import com.yongdrel.auditlogservice.application.exception.DuplicateAuditEventException;
import com.yongdrel.auditlogservice.application.exception.ReferenceValidationException;
import com.yongdrel.auditlogservice.application.port.in.RecordAuditEventUseCase;
import com.yongdrel.auditlogservice.application.port.out.AuditEventRepositoryPort;
import com.yongdrel.auditlogservice.application.port.out.SourceServiceRegistryPort;
import com.yongdrel.auditlogservice.application.port.out.TenantReferencePort;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;
import com.yongdrel.auditlogservice.domain.model.Caller;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class AuditRecordingService implements RecordAuditEventUseCase {

    private final AuditEventRepositoryPort events;
    private final SourceServiceRegistryPort sources;
    private final TenantReferencePort tenants;
    private final Clock clock;

    public AuditRecordingService(AuditEventRepositoryPort events, SourceServiceRegistryPort sources,
                                 TenantReferencePort tenants, Clock clock) {
        this.events = events;
        this.sources = sources;
        this.tenants = tenants;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AuditEvent record(AuditEventDraft draft, Caller caller) {
        AccessPolicy.requireWriter(caller);
        AuditEvent event = AuditEvent.create(draft, clock.instant().truncatedTo(ChronoUnit.MICROS));
        events.findByEventId(event.eventId()).ifPresent(existing -> {
            throw new DuplicateAuditEventException(
                    "Audit event " + event.eventId() + " was already recorded as id " + existing.id());
        });
        verifyReferences(event);
        return events.append(event);
    }

    @Override
    @Transactional
    public Optional<AuditEvent> recordFromBroker(AuditEventDraft draft) {
        AuditEvent event = AuditEvent.create(draft, clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (events.findByEventId(event.eventId()).isPresent()) {
            return Optional.empty();
        }
        verifyReferences(event);
        return Optional.of(events.append(event));
    }

    private void verifyReferences(AuditEvent event) {
        if (!sources.isKnown(event.sourceService())) {
            throw new ReferenceValidationException("Unknown sourceService '" + event.sourceService() + "'");
        }
        if (event.tenantId() != null && tenants.isEnabled() && !tenants.tenantExists(event.tenantId())) {
            throw new ReferenceValidationException("Tenant " + event.tenantId() + " does not exist");
        }
    }
}
