package com.yongdrel.auditlogservice.application.port.in;

import com.yongdrel.auditlogservice.application.model.AuditSearchCriteria;
import com.yongdrel.auditlogservice.application.model.PageResult;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.Caller;

import java.util.List;

public interface AuditQueryUseCase {

    PageResult<AuditEvent> search(AuditSearchCriteria criteria, int page, int size, Caller caller);

    AuditEvent get(Long id, Caller caller);

    /** All visible events sharing a correlation id, oldest first (one business transaction). */
    List<AuditEvent> timeline(String correlationId, Caller caller);
}
