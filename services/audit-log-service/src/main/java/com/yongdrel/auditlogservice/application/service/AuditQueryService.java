package com.yongdrel.auditlogservice.application.service;

import com.yongdrel.auditlogservice.application.exception.ResourceNotFoundException;
import com.yongdrel.auditlogservice.application.model.AuditSearchCriteria;
import com.yongdrel.auditlogservice.application.model.PageResult;
import com.yongdrel.auditlogservice.application.port.in.AuditQueryUseCase;
import com.yongdrel.auditlogservice.application.port.out.AuditEventRepositoryPort;
import com.yongdrel.auditlogservice.domain.exception.InvalidAuditEventException;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.Caller;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuditQueryService implements AuditQueryUseCase {

    public static final Duration MAX_RANGE = Duration.ofDays(366);
    public static final int TIMELINE_LIMIT = 500;

    private final AuditEventRepositoryPort events;

    public AuditQueryService(AuditEventRepositoryPort events) {
        this.events = events;
    }

    @Override
    public PageResult<AuditEvent> search(AuditSearchCriteria criteria, int page, int size, Caller caller) {
        validateRange(criteria);
        return events.search(AccessPolicy.scope(criteria, caller), page, size);
    }

    @Override
    public AuditEvent get(Long id, Caller caller) {
        AccessPolicy.scope(AuditSearchCriteria.none(), caller); // rejects SERVICE / organizer without tenant
        return events.findById(id)
                .filter(event -> AccessPolicy.canView(event, caller))
                .orElseThrow(() -> new ResourceNotFoundException("Audit event " + id + " not found"));
    }

    @Override
    public List<AuditEvent> timeline(String correlationId, Caller caller) {
        if (correlationId == null || !AuditEvent.CORRELATION_ID.matcher(correlationId).matches()) {
            throw new InvalidAuditEventException("correlationId must be 8-64 characters [A-Za-z0-9._-]");
        }
        List<AuditEvent> result = events.timeline(
                AccessPolicy.scope(AuditSearchCriteria.forCorrelation(correlationId), caller), TIMELINE_LIMIT);
        if (result.isEmpty()) {
            throw new ResourceNotFoundException("No visible audit events for correlationId " + correlationId);
        }
        return result;
    }

    private static void validateRange(AuditSearchCriteria c) {
        if (c.from() != null && c.to() != null) {
            if (!c.from().isBefore(c.to())) {
                throw new InvalidAuditEventException("'from' must be before 'to'");
            }
            if (Duration.between(c.from(), c.to()).compareTo(MAX_RANGE) > 0) {
                throw new InvalidAuditEventException("Search range cannot exceed 366 days");
            }
        }
    }
}
