package com.yongdrel.reportingservice.adapter.in.web;

import com.yongdrel.reportingservice.application.exception.ForbiddenOperationException;
import com.yongdrel.reportingservice.application.exception.ResourceNotFoundException;
import com.yongdrel.reportingservice.application.model.PageResult;
import com.yongdrel.reportingservice.application.model.ReportRequest;
import com.yongdrel.reportingservice.application.port.in.ReportUseCase;
import com.yongdrel.reportingservice.config.SecurityConfig;
import com.yongdrel.reportingservice.domain.exception.InvalidReportRequestException;
import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.Role;
import com.yongdrel.reportingservice.domain.model.Summaries;
import com.yongdrel.reportingservice.domain.service.ReportCalculator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.admin;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.organizer;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.service;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.student;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.token;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class ReportControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ReportUseCase reports;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final Caller ORG = new Caller(3L, Role.ORGANIZER, 10L);

    private static Summaries.SalesSummary sales() {
        return ReportCalculator.sales(List.of(new Summaries.StatusTotals(
                com.yongdrel.reportingservice.domain.model.OrderStatus.CONFIRMED, 2, 3, new BigDecimal("150"))), List.of());
    }

    @Test
    void salesPassesFiltersAndCaller() throws Exception {
        when(reports.sales(any(), any())).thenReturn(sales());

        mvc.perform(get("/api/reports/sales").with(organizer())
                        .param("eventId", "5").param("from", "2026-11-01T00:00:00Z").param("to", "2026-11-30T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("BTN"))
                .andExpect(jsonPath("$.revenue").value(150.00))
                .andExpect(jsonPath("$.ticketsSold").value(3));

        verify(reports).sales(new ReportRequest(null, 5L, Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-30T00:00:00Z")), ORG);
    }

    @Test
    void ticketsPrizesPayoutsOverviewSummary() throws Exception {
        when(reports.tickets(any(), any())).thenReturn(new Summaries.TicketSummary(100, 3, 1, 96, new BigDecimal("3.00")));
        when(reports.prizes(any(), any())).thenReturn(ReportCalculator.prizes(List.of(), true));
        when(reports.payouts(any(), any())).thenReturn(ReportCalculator.payouts(List.of()));
        when(reports.overview(any(), any())).thenReturn(new Summaries.Overview(10L, 1, java.util.Map.of(EventStatus.OPEN, 1L),
                sales(), new Summaries.TicketSummary(100, 3, 1, 96, new BigDecimal("3.00")),
                ReportCalculator.prizes(List.of(), false), ReportCalculator.payouts(List.of()), new BigDecimal("150.00")));
        when(reports.eventSummary(eq(5L), any())).thenReturn(new Summaries.EventSummary(5L, 10L, "Losar Raffle",
                EventStatus.OPEN, new BigDecimal("50.00"), sales(), new Summaries.TicketSummary(100, 3, 1, 96,
                new BigDecimal("3.00")), ReportCalculator.prizes(List.of(), true), ReportCalculator.payouts(List.of()),
                new BigDecimal("150.00")));

        mvc.perform(get("/api/reports/tickets").param("eventId", "5").with(organizer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(96));
        mvc.perform(get("/api/reports/prizes").with(organizer())).andExpect(status().isOk());
        mvc.perform(get("/api/reports/payouts").with(admin()).param("tenantId", "10")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/overview").with(organizer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eventsByStatus.OPEN").value(1));
        mvc.perform(get("/api/reports/events/5/summary").with(organizer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Losar Raffle"))
                .andExpect(jsonPath("$.netAfterPrizes").value(150.00));
    }

    @Test
    void eventsPage() throws Exception {
        when(reports.events(eq(null), eq(EventStatus.OPEN), eq(0), eq(20), any())).thenReturn(new PageResult<>(List.of(
                new Summaries.EventRow(5L, "Losar Raffle", EventStatus.OPEN, new BigDecimal("50.00"), 100, 3,
                        new BigDecimal("150.00"), new BigDecimal("3.00"))), 0, 20, 1));

        mvc.perform(get("/api/reports/events").param("status", "OPEN").with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(5))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void studentGetsOwnPurchasesOnly() throws Exception {
        when(reports.myPurchases(any())).thenReturn(new Summaries.MyPurchases(100L, "BTN", 3, new BigDecimal("150.00"), List.of()));

        mvc.perform(get("/api/reports/me/purchases").with(student()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.buyerId").value(100));
        mvc.perform(get("/api/reports/sales").with(student())).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/me/purchases").with(organizer())).andExpect(status().isForbidden());
    }

    @Test
    void servicesCannotReadReports() throws Exception {
        mvc.perform(get("/api/reports/overview").with(service())).andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    @Test
    void invalidJwtIs401() throws Exception {
        mvc.perform(get("/api/reports/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonNumericSubjectIs403() throws Exception {
        mvc.perform(get("/api/reports/overview").with(token("alice", "ORGANIZER", 10L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Token subject must be a numeric user id"));
    }

    @Test
    void otherTenantIs403AndForeignEventIs404() throws Exception {
        when(reports.sales(any(), any())).thenThrow(new ForbiddenOperationException("Tenant 20 does not belong to the caller"));
        when(reports.eventSummary(eq(7L), any())).thenThrow(new ResourceNotFoundException("Event 7 not found"));

        mvc.perform(get("/api/reports/sales").param("tenantId", "20").with(organizer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/events/7/summary").with(organizer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Event 7 not found"));
    }

    @Test
    void invalidPeriodFromUseCaseIs400() throws Exception {
        when(reports.sales(any(), any())).thenThrow(new InvalidReportRequestException("'from' must be before 'to'"));
        mvc.perform(get("/api/reports/sales").with(organizer())
                        .param("from", "2026-11-30T00:00:00Z").param("to", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reports/sales?eventId=0", "/api/reports/sales?from=yesterday",
            "/api/reports/events?size=500", "/api/reports/events?status=FINISHED", "/api/reports/events/abc/summary",
            "/api/reports/tickets?tenantId=-1"})
    void badParametersAre400(String url) throws Exception {
        mvc.perform(get(url).with(admin())).andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Test
    void unknownPathIs404() throws Exception {
        when(reports.events(any(), any(), anyInt(), anyInt(), any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        mvc.perform(get("/api/reports/nothing-here").with(admin())).andExpect(status().isNotFound());
    }
}
