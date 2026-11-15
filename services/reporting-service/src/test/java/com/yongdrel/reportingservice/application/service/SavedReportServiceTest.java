package com.yongdrel.reportingservice.application.service;

import com.yongdrel.reportingservice.application.exception.ConflictException;
import com.yongdrel.reportingservice.application.exception.ForbiddenOperationException;
import com.yongdrel.reportingservice.application.exception.ResourceNotFoundException;
import com.yongdrel.reportingservice.application.model.ReportRequest;
import com.yongdrel.reportingservice.application.model.SavedReportCommand;
import com.yongdrel.reportingservice.application.port.in.ReportUseCase;
import com.yongdrel.reportingservice.application.port.out.ProjectionRepositoryPort;
import com.yongdrel.reportingservice.application.port.out.SavedReportRepositoryPort;
import com.yongdrel.reportingservice.domain.model.ReportType;
import com.yongdrel.reportingservice.domain.model.SavedReport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.yongdrel.reportingservice.TestData.ADMIN;
import static com.yongdrel.reportingservice.TestData.COLLEAGUE;
import static com.yongdrel.reportingservice.TestData.ORGANIZER;
import static com.yongdrel.reportingservice.TestData.OTHER_ORGANIZER;
import static com.yongdrel.reportingservice.TestData.T0;
import static com.yongdrel.reportingservice.TestData.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedReportServiceTest {

    @Mock SavedReportRepositoryPort reports;
    @Mock ProjectionRepositoryPort projections;
    @Mock ReportUseCase reportUseCase;
    private SavedReportService service;

    private final SavedReportCommand sales = new SavedReportCommand("Weekly sales", ReportType.SALES, null, null, null);

    @BeforeEach
    void setUp() {
        service = new SavedReportService(reports, projections, reportUseCase, Clock.fixed(T0, ZoneOffset.UTC));
        lenient().when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(projections.findEvent(5L)).thenReturn(Optional.of(event()));
        lenient().when(projections.findEvent(99L)).thenReturn(Optional.empty());
    }

    private SavedReport stored(ReportType type, Long eventId) {
        return new SavedReport(1L, 10L, 3L, "Weekly sales", type, eventId, null, null, T0, T0, 0L);
    }

    @Test
    void createOwnedByCaller() {
        SavedReport r = service.create(sales, ORGANIZER);
        assertThat(r.getOwnerId()).isEqualTo(3L);
        assertThat(r.getTenantId()).isEqualTo(10L);
        assertThat(r.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void duplicateNameIs409() {
        when(reports.nameTaken(10L, "Weekly sales", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(sales, ORGANIZER)).isInstanceOf(ConflictException.class);
        verify(reports, never()).save(any());
    }

    @Test
    void eventMustBelongToTenant() {
        assertThat(service.create(new SavedReportCommand("E5", ReportType.EVENT_SUMMARY, 5L, null, null), ORGANIZER)
                .getEventId()).isEqualTo(5L);
        assertThatThrownBy(() -> service.create(new SavedReportCommand("E99", ReportType.SALES, 99L, null, null), ORGANIZER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.create(new SavedReportCommand("E5", ReportType.SALES, 5L, null, null), OTHER_ORGANIZER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void onlyOrganizersManageSavedReports() {
        assertThatThrownBy(() -> service.create(sales, ADMIN)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.list(ADMIN)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void colleaguesCanReadButNotChange() {
        when(reports.findById(1L)).thenReturn(Optional.of(stored(ReportType.SALES, null)));

        assertThat(service.get(1L, COLLEAGUE).getId()).isEqualTo(1L);
        assertThatThrownBy(() -> service.update(1L, sales, COLLEAGUE)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.delete(1L, COLLEAGUE)).isInstanceOf(ForbiddenOperationException.class);
        verify(reports, never()).deleteById(any());
    }

    @Test
    void otherTenantSees404() {
        when(reports.findById(1L)).thenReturn(Optional.of(stored(ReportType.SALES, null)));
        assertThatThrownBy(() -> service.get(1L, OTHER_ORGANIZER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ownerUpdatesAndDeletes() {
        when(reports.findById(1L)).thenReturn(Optional.of(stored(ReportType.SALES, null)));

        SavedReport updated = service.update(1L, new SavedReportCommand("Renamed", ReportType.TICKETS, null, null, null), ORGANIZER);
        service.delete(1L, ORGANIZER);

        assertThat(updated.getName()).isEqualTo("Renamed");
        verify(reports).nameTaken(10L, "Renamed", 1L);
        verify(reports).deleteById(1L);
    }

    @Test
    void renameToTakenNameIs409() {
        when(reports.findById(1L)).thenReturn(Optional.of(stored(ReportType.SALES, null)));
        when(reports.nameTaken(10L, "Taken", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(1L, new SavedReportCommand("Taken", ReportType.SALES, null, null, null), ORGANIZER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listReturnsTenantReports() {
        when(reports.findByTenant(10L)).thenReturn(List.of(stored(ReportType.SALES, null)));
        assertThat(service.list(ORGANIZER)).hasSize(1);
    }

    @Test
    void unknownIs404() {
        when(reports.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L, ORGANIZER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void runDispatchesByType(ReportType type) {
        when(reports.findById(1L)).thenReturn(Optional.of(stored(type, 5L)));
        service.run(1L, ORGANIZER);
        ReportRequest request = new ReportRequest(10L, 5L, null, null);
        switch (type) {
            case OVERVIEW -> verify(reportUseCase).overview(request, ORGANIZER);
            case EVENT_SUMMARY -> verify(reportUseCase).eventSummary(5L, ORGANIZER);
            case SALES -> verify(reportUseCase).sales(request, ORGANIZER);
            case TICKETS -> verify(reportUseCase).tickets(request, ORGANIZER);
            case PRIZES -> verify(reportUseCase).prizes(request, ORGANIZER);
            case PAYOUTS -> verify(reportUseCase).payouts(eq(request), eq(ORGANIZER));
        }
    }

    @Test
    void createWithoutEventSkipsEventLookup() {
        service.create(sales, ORGANIZER);
        verify(projections, never()).findEvent(any());
        verify(reports).nameTaken(eq(10L), eq("Weekly sales"), isNull());
    }
}
