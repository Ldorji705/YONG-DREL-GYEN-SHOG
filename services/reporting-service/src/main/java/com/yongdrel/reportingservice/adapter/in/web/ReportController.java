package com.yongdrel.reportingservice.adapter.in.web;

import com.yongdrel.reportingservice.adapter.in.web.dto.PageResponse;
import com.yongdrel.reportingservice.application.model.ReportRequest;
import com.yongdrel.reportingservice.application.port.in.ReportUseCase;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.Summaries;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Organizer-scoped reports. Organizers are always confined to their own tenant (tenantId optional, must match);
 * admins may pass any tenantId or none (platform-wide). from/to filter orders by creation time.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportUseCase reports;

    public ReportController(ReportUseCase reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    public Summaries.Overview overview(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) @Positive Long tenantId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.overview(new ReportRequest(tenantId, null, from, to), JwtCallerMapper.toCaller(jwt));
    }

    @GetMapping("/events")
    public PageResponse<Summaries.EventRow> events(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) @Positive Long tenantId,
                                                   @RequestParam(required = false) EventStatus status,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(reports.events(tenantId, status, page, size, JwtCallerMapper.toCaller(jwt)), r -> r);
    }

    @GetMapping("/events/{eventId}/summary")
    public Summaries.EventSummary eventSummary(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long eventId) {
        return reports.eventSummary(eventId, JwtCallerMapper.toCaller(jwt));
    }

    @GetMapping("/sales")
    public Summaries.SalesSummary sales(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) @Positive Long tenantId,
                                        @RequestParam(required = false) @Positive Long eventId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.sales(new ReportRequest(tenantId, eventId, from, to), JwtCallerMapper.toCaller(jwt));
    }

    @GetMapping("/tickets")
    public Summaries.TicketSummary tickets(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) @Positive Long tenantId,
                                           @RequestParam(required = false) @Positive Long eventId) {
        return reports.tickets(ReportRequest.of(tenantId, eventId), JwtCallerMapper.toCaller(jwt));
    }

    @GetMapping("/prizes")
    public Summaries.PrizeSummary prizes(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) @Positive Long tenantId,
                                         @RequestParam(required = false) @Positive Long eventId) {
        return reports.prizes(ReportRequest.of(tenantId, eventId), JwtCallerMapper.toCaller(jwt));
    }

    @GetMapping("/payouts")
    public Summaries.PayoutSummary payouts(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) @Positive Long tenantId,
                                           @RequestParam(required = false) @Positive Long eventId) {
        return reports.payouts(ReportRequest.of(tenantId, eventId), JwtCallerMapper.toCaller(jwt));
    }

    /** A student's own purchases across all events (ownership = JWT sub). */
    @GetMapping("/me/purchases")
    public Summaries.MyPurchases myPurchases(@AuthenticationPrincipal Jwt jwt) {
        return reports.myPurchases(JwtCallerMapper.toCaller(jwt));
    }
}
