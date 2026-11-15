package com.yongdrel.reportingservice.application.model;

import com.yongdrel.reportingservice.domain.model.ReportType;

import java.time.Instant;

public record SavedReportCommand(String name, ReportType reportType, Long eventId, Instant from, Instant to) {
}
