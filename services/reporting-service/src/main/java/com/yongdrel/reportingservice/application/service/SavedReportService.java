package com.yongdrel.reportingservice.application.service;

import com.yongdrel.reportingservice.application.exception.ConflictException;
import com.yongdrel.reportingservice.application.exception.ForbiddenOperationException;
import com.yongdrel.reportingservice.application.exception.ResourceNotFoundException;
import com.yongdrel.reportingservice.application.model.ReportRequest;
import com.yongdrel.reportingservice.application.model.SavedReportCommand;
import com.yongdrel.reportingservice.application.port.in.ReportUseCase;
import com.yongdrel.reportingservice.application.port.in.SavedReportUseCase;
import com.yongdrel.reportingservice.application.port.out.ProjectionRepositoryPort;
import com.yongdrel.reportingservice.application.port.out.SavedReportRepositoryPort;
import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.SavedReport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Saved report definitions. Organizers of a tenant can all read and run them; only the creator may change or
 * delete one. Names are unique per tenant (409). Reports of other tenants are 404.
 */
@Service
@Transactional
public class SavedReportService implements SavedReportUseCase {

    private final SavedReportRepositoryPort reports;
    private final ProjectionRepositoryPort projections;
    private final ReportUseCase reportUseCase;
    private final Clock clock;

    public SavedReportService(SavedReportRepositoryPort reports, ProjectionRepositoryPort projections,
                              ReportUseCase reportUseCase, Clock clock) {
        this.reports = reports;
        this.projections = projections;
        this.reportUseCase = reportUseCase;
        this.clock = clock;
    }

    @Override
    public SavedReport create(SavedReportCommand c, Caller caller) {
        Long tenant = AccessPolicy.requireOrganizerTenant(caller);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        SavedReport report = new SavedReport(null, tenant, caller.userId(), c.name(), c.reportType(), c.eventId(),
                c.from(), c.to(), now, now, null);
        requireOwnEvent(report);
        requireUniqueName(report, null);
        return reports.save(report);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedReport> list(Caller caller) {
        return reports.findByTenant(AccessPolicy.requireOrganizerTenant(caller));
    }

    @Override
    @Transactional(readOnly = true)
    public SavedReport get(Long id, Caller caller) {
        return visible(id, caller);
    }

    @Override
    public SavedReport update(Long id, SavedReportCommand c, Caller caller) {
        SavedReport report = owned(id, caller);
        report.update(c.name(), c.reportType(), c.eventId(), c.from(), c.to(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        requireOwnEvent(report);
        requireUniqueName(report, id);
        return reports.save(report);
    }

    @Override
    public void delete(Long id, Caller caller) {
        owned(id, caller);
        reports.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Object run(Long id, Caller caller) {
        SavedReport r = visible(id, caller);
        ReportRequest request = new ReportRequest(r.getTenantId(), r.getEventId(), r.getFrom(), r.getTo());
        return switch (r.getReportType()) {
            case OVERVIEW -> reportUseCase.overview(request, caller);
            case EVENT_SUMMARY -> reportUseCase.eventSummary(r.getEventId(), caller);
            case SALES -> reportUseCase.sales(request, caller);
            case TICKETS -> reportUseCase.tickets(request, caller);
            case PRIZES -> reportUseCase.prizes(request, caller);
            case PAYOUTS -> reportUseCase.payouts(request, caller);
        };
    }

    private SavedReport visible(Long id, Caller caller) {
        Long tenant = AccessPolicy.requireOrganizerTenant(caller);
        return reports.findById(id)
                .filter(r -> r.getTenantId().equals(tenant))
                .orElseThrow(() -> new ResourceNotFoundException("Saved report " + id + " not found"));
    }

    private SavedReport owned(Long id, Caller caller) {
        SavedReport report = visible(id, caller);
        if (!report.isOwnedBy(caller.userId())) {
            throw new ForbiddenOperationException("Only the organizer who created saved report " + id + " can change it");
        }
        return report;
    }

    /** Cross-service reference: the event must exist and belong to the organizer's tenant. */
    private void requireOwnEvent(SavedReport report) {
        if (report.getEventId() != null && projections.findEvent(report.getEventId())
                .filter(e -> e.tenantId().equals(report.getTenantId())).isEmpty()) {
            throw new ResourceNotFoundException("Event " + report.getEventId() + " not found");
        }
    }

    private void requireUniqueName(SavedReport report, Long excludeId) {
        if (reports.nameTaken(report.getTenantId(), report.getName(), excludeId)) {
            throw new ConflictException("A saved report named '" + report.getName() + "' already exists in this tenant");
        }
    }
}
