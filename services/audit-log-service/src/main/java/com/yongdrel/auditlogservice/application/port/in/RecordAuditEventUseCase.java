package com.yongdrel.auditlogservice.application.port.in;

import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;
import com.yongdrel.auditlogservice.domain.model.Caller;

import java.util.Optional;

public interface RecordAuditEventUseCase {

    /** REST path: caller must be SERVICE or ADMIN; a repeated eventId is a 409. */
    AuditEvent record(AuditEventDraft draft, Caller caller);

    /** Broker path: trusted internal producer; a repeated eventId (redelivery) is ignored and returns empty. */
    Optional<AuditEvent> recordFromBroker(AuditEventDraft draft);
}
