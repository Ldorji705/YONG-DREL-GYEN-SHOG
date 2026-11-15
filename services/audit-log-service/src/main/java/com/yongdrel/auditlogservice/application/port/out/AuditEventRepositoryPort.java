package com.yongdrel.auditlogservice.application.port.out;

import com.yongdrel.auditlogservice.application.model.AuditSearchCriteria;
import com.yongdrel.auditlogservice.application.model.PageResult;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepositoryPort {
    Optional<AuditEvent> findByEventId(UUID eventId);
    AuditEvent append(AuditEvent event);
    Optional<AuditEvent> findById(Long id);
    PageResult<AuditEvent> search(AuditSearchCriteria criteria, int page, int size);
    List<AuditEvent> timeline(AuditSearchCriteria criteria, int limit);
}
