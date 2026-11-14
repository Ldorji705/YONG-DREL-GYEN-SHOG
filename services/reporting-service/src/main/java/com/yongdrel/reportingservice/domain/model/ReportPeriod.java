package com.yongdrel.reportingservice.domain.model;

import com.yongdrel.reportingservice.domain.exception.InvalidReportRequestException;

import java.time.Duration;
import java.time.Instant;

public final class ReportPeriod {

    public static final Duration MAX = Duration.ofDays(366);

    private ReportPeriod() {
    }

    /** Both optional; if both given, from &lt; to and at most 366 days apart. */
    public static void validate(Instant from, Instant to) {
        if (from != null && to != null) {
            if (!from.isBefore(to)) {
                throw new InvalidReportRequestException("'from' must be before 'to'");
            }
            if (Duration.between(from, to).compareTo(MAX) > 0) {
                throw new InvalidReportRequestException("Report period cannot exceed 366 days");
            }
        }
    }
}
