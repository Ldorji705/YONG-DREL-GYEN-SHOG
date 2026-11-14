package com.yongdrel.reportingservice.domain;

import com.yongdrel.reportingservice.domain.exception.InvalidReportRequestException;
import com.yongdrel.reportingservice.domain.model.ReportPeriod;
import com.yongdrel.reportingservice.domain.model.ReportType;
import com.yongdrel.reportingservice.domain.model.SavedReport;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.yongdrel.reportingservice.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavedReportTest {

    private SavedReport report() {
        return new SavedReport(1L, 10L, 3L, " Weekly sales ", ReportType.SALES, null, T0, T0.plus(Duration.ofDays(7)),
                T0, T0, 0L);
    }

    @Test
    void createTrimsAndScopes() {
        SavedReport r = report();
        assertThat(r.getName()).isEqualTo("Weekly sales");
        assertThat(r.isOwnedBy(3L)).isTrue();
        assertThat(r.isOwnedBy(6L)).isFalse();
        assertThat(r.scope().tenantId()).isEqualTo(10L);
        assertThat(r.scope().from()).isEqualTo(T0);
    }

    @Test
    void updateChangesDefinition() {
        SavedReport r = report();
        r.update("Event 5", ReportType.EVENT_SUMMARY, 5L, null, null, T0.plusSeconds(5));
        assertThat(r.getReportType()).isEqualTo(ReportType.EVENT_SUMMARY);
        assertThat(r.getEventId()).isEqualTo(5L);
        assertThat(r.getUpdatedAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void invalidDefinitions() {
        SavedReport r = report();
        assertThatThrownBy(() -> r.update(" ", ReportType.SALES, null, null, null, T0)).hasMessageContaining("name");
        assertThatThrownBy(() -> r.update("x".repeat(81), ReportType.SALES, null, null, null, T0)).hasMessageContaining("name");
        assertThatThrownBy(() -> r.update("x", null, null, null, null, T0)).hasMessageContaining("reportType");
        assertThatThrownBy(() -> r.update("x", ReportType.EVENT_SUMMARY, null, null, null, T0)).hasMessageContaining("eventId");
        assertThatThrownBy(() -> r.update("x", ReportType.SALES, null, T0, T0, T0)).hasMessageContaining("before");
    }

    @Test
    void periodLimits() {
        ReportPeriod.validate(null, T0);
        ReportPeriod.validate(T0, T0.plus(Duration.ofDays(366)));
        assertThatThrownBy(() -> ReportPeriod.validate(T0, T0.plus(Duration.ofDays(366)).plusSeconds(1)))
                .isInstanceOf(InvalidReportRequestException.class).hasMessageContaining("366");
    }
}
