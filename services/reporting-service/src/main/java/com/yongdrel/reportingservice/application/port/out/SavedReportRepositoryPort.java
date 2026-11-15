package com.yongdrel.reportingservice.application.port.out;

import com.yongdrel.reportingservice.domain.model.SavedReport;
import java.util.List;
import java.util.Optional;

public interface SavedReportRepositoryPort {
    SavedReport save(SavedReport report);
    List<SavedReport> findByTenant(Long tenantId);
    Optional<SavedReport> findById(Long id);
    void deleteById(Long id);
    boolean nameTaken(Long tenantId, String name, Long excludeId);
}
