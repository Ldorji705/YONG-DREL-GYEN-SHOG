package com.yongdrel.reportingservice.adapter.in.web.dto;

import com.yongdrel.reportingservice.application.model.SavedReportCommand;
import com.yongdrel.reportingservice.domain.model.ReportType;
import com.yongdrel.reportingservice.domain.model.SavedReport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SavedReportDtos {

    private SavedReportDtos() {
    }

    public record SavedReportRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull ReportType reportType,
            @Positive Long eventId,
            Instant from,
            Instant to
    ) {
        public SavedReportCommand toCommand() {
            return new SavedReportCommand(name, reportType, eventId, from, to);
        }
    }

    public record SavedReportResponse(Long id, Long tenantId, Long ownerId, String name, ReportType reportType,
                                      Long eventId, Instant from, Instant to, Instant createdAt, Instant updatedAt) {

        public static SavedReportResponse from(SavedReport r) {
            return new SavedReportResponse(r.getId(), r.getTenantId(), r.getOwnerId(), r.getName(), r.getReportType(),
                    r.getEventId(), r.getFrom(), r.getTo(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
