package com.yongdrel.reportingservice.application.service;

import com.yongdrel.reportingservice.application.exception.ResourceNotFoundException;
import com.yongdrel.reportingservice.application.model.PageResult;
import com.yongdrel.reportingservice.application.model.ReportRequest;
import com.yongdrel.reportingservice.application.port.in.ReportUseCase;
import com.yongdrel.reportingservice.application.port.out.ProjectionRepositoryPort;
import com.yongdrel.reportingservice.application.port.out.ReportQueryPort;
import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.ReportPeriod;
import com.yongdrel.reportingservice.domain.model.ReportScope;
import com.yongdrel.reportingservice.domain.model.Summaries;
import com.yongdrel.reportingservice.domain.model.Summaries.StatusTotals;
import com.yongdrel.reportingservice.domain.service.ReportCalculator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportService implements ReportUseCase {

    private final ReportQueryPort queries;
    private final ProjectionRepositoryPort projections;

    public ReportService(ReportQueryPort queries, ProjectionRepositoryPort projections) {
        this.queries = queries;
        this.projections = projections;
    }

    @Override
    public Summaries.Overview overview(ReportRequest request, Caller caller) {
        ReportScope scope = scope(request, caller);
        Map<EventStatus, Long> byStatus = queries.eventCountsByStatus(scope);
        Summaries.SalesSummary sales = sales(scope);
        Summaries.PrizeSummary prizes = ReportCalculator.prizes(queries.prizes(scope), false);
        return new Summaries.Overview(scope.tenantId(), byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus, sales, ReportCalculator.tickets(queries.capacity(scope), queries.orderTotals(scope)), prizes,
                ReportCalculator.payouts(queries.payoutTotals(scope)), ReportCalculator.netAfterPrizes(sales, prizes));
    }

    @Override
    public PageResult<Summaries.EventRow> events(Long tenantId, EventStatus status, int page, int size, Caller caller) {
        Long tenant = AccessPolicy.reportTenant(caller, tenantId);
        PageResult<EventProjection> events = queries.events(tenant, status, page, size);
        Map<Long, StatusTotals> confirmed = queries.confirmedTotalsByEvent(
                events.content().stream().map(EventProjection::id).toList());
        List<Summaries.EventRow> rows = events.content().stream().map(e -> {
            StatusTotals t = confirmed.getOrDefault(e.id(), new StatusTotals(OrderStatus.CONFIRMED, 0, 0, BigDecimal.ZERO));
            return new Summaries.EventRow(e.id(), e.name(), e.status(), e.ticketPrice(), e.totalTickets(), t.tickets(),
                    ReportCalculator.money(t.amount()), ReportCalculator.percent(t.tickets(), e.totalTickets()));
        }).toList();
        return new PageResult<>(rows, events.page(), events.size(), events.totalElements());
    }

    @Override
    public Summaries.EventSummary eventSummary(Long eventId, Caller caller) {
        AccessPolicy.reportTenant(caller, null); // role / tenant-claim checks
        EventProjection event = visibleEvent(eventId, caller);
        ReportScope scope = new ReportScope(event.tenantId(), eventId, null, null);
        Summaries.SalesSummary sales = sales(scope);
        Summaries.PrizeSummary prizes = ReportCalculator.prizes(queries.prizes(scope), true);
        return new Summaries.EventSummary(event.id(), event.tenantId(), event.name(), event.status(), event.ticketPrice(),
                sales, ReportCalculator.tickets(event.totalTickets(), queries.orderTotals(scope)), prizes,
                ReportCalculator.payouts(queries.payoutTotals(scope)), ReportCalculator.netAfterPrizes(sales, prizes));
    }

    @Override
    public Summaries.SalesSummary sales(ReportRequest request, Caller caller) {
        return sales(scope(request, caller));
    }

    @Override
    public Summaries.TicketSummary tickets(ReportRequest request, Caller caller) {
        ReportScope scope = scope(request, caller);
        return ReportCalculator.tickets(queries.capacity(scope), queries.orderTotals(scope));
    }

    @Override
    public Summaries.PrizeSummary prizes(ReportRequest request, Caller caller) {
        ReportScope scope = scope(request, caller);
        return ReportCalculator.prizes(queries.prizes(scope), scope.eventId() != null);
    }

    @Override
    public Summaries.PayoutSummary payouts(ReportRequest request, Caller caller) {
        return ReportCalculator.payouts(queries.payoutTotals(scope(request, caller)));
    }

    @Override
    public Summaries.MyPurchases myPurchases(Caller caller) {
        AccessPolicy.requireStudent(caller);
        Map<Long, Map<OrderStatus, StatusTotals>> totals = queries.buyerTotals(caller.userId());
        Map<Long, String> names = queries.eventNames(totals.keySet());
        List<Summaries.PurchaseRow> rows = new ArrayList<>();
        long tickets = 0;
        BigDecimal spent = BigDecimal.ZERO;
        for (var entry : totals.entrySet()) {
            Map<OrderStatus, StatusTotals> byStatus = entry.getValue();
            StatusTotals confirmed = byStatus.getOrDefault(OrderStatus.CONFIRMED,
                    new StatusTotals(OrderStatus.CONFIRMED, 0, 0, BigDecimal.ZERO));
            long pending = byStatus.values().stream().filter(t -> t.status().isPending()).mapToLong(StatusTotals::orders).sum();
            long failed = byStatus.containsKey(OrderStatus.FAILED) ? byStatus.get(OrderStatus.FAILED).orders() : 0;
            rows.add(new Summaries.PurchaseRow(entry.getKey(), names.get(entry.getKey()), confirmed.tickets(),
                    ReportCalculator.money(confirmed.amount()), pending, failed));
            tickets += confirmed.tickets();
            spent = spent.add(confirmed.amount());
        }
        rows.sort(Comparator.comparing(Summaries.PurchaseRow::eventId));
        return new Summaries.MyPurchases(caller.userId(), ReportCalculator.CURRENCY, tickets,
                ReportCalculator.money(spent), rows);
    }

    /** Resolves tenant scoping, validates the period and checks that a requested event is visible. */
    ReportScope scope(ReportRequest request, Caller caller) {
        Long tenant = AccessPolicy.reportTenant(caller, request.tenantId());
        ReportPeriod.validate(request.from(), request.to());
        if (request.eventId() != null) {
            EventProjection event = visibleEvent(request.eventId(), caller);
            if (tenant != null && !tenant.equals(event.tenantId())) {
                throw new ResourceNotFoundException("Event " + request.eventId() + " not found");
            }
            tenant = event.tenantId();
        }
        return new ReportScope(tenant, request.eventId(), request.from(), request.to());
    }

    /** Events of other tenants are reported as 404 so their existence is not revealed. */
    private EventProjection visibleEvent(Long eventId, Caller caller) {
        return projections.findEvent(eventId)
                .filter(e -> AccessPolicy.canSeeTenant(caller, e.tenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    private Summaries.SalesSummary sales(ReportScope scope) {
        return ReportCalculator.sales(queries.orderTotals(scope), queries.confirmedOrders(scope));
    }
}
