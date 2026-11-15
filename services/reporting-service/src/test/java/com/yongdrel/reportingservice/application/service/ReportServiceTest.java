package com.yongdrel.reportingservice.application.service;

import com.yongdrel.reportingservice.application.exception.ForbiddenOperationException;
import com.yongdrel.reportingservice.application.exception.ResourceNotFoundException;
import com.yongdrel.reportingservice.application.model.PageResult;
import com.yongdrel.reportingservice.application.model.ReportRequest;
import com.yongdrel.reportingservice.application.port.out.ProjectionRepositoryPort;
import com.yongdrel.reportingservice.application.port.out.ReportQueryPort;
import com.yongdrel.reportingservice.domain.exception.InvalidReportRequestException;
import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.PayoutStatus;
import com.yongdrel.reportingservice.domain.model.ReportScope;
import com.yongdrel.reportingservice.domain.model.Summaries;
import com.yongdrel.reportingservice.domain.model.Summaries.StatusTotals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yongdrel.reportingservice.TestData.ADMIN;
import static com.yongdrel.reportingservice.TestData.ORGANIZER;
import static com.yongdrel.reportingservice.TestData.ORGANIZER_WITHOUT_TENANT;
import static com.yongdrel.reportingservice.TestData.OTHER_ORGANIZER;
import static com.yongdrel.reportingservice.TestData.SERVICE;
import static com.yongdrel.reportingservice.TestData.STUDENT;
import static com.yongdrel.reportingservice.TestData.T0;
import static com.yongdrel.reportingservice.TestData.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportQueryPort queries;
    @Mock ProjectionRepositoryPort projections;
    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(queries, projections);
        lenient().when(projections.findEvent(5L)).thenReturn(Optional.of(event()));
        lenient().when(queries.orderTotals(any())).thenReturn(List.of(
                new StatusTotals(OrderStatus.CONFIRMED, 2, 3, new BigDecimal("150")),
                new StatusTotals(OrderStatus.AWAITING_VERIFICATION, 1, 1, new BigDecimal("50"))));
        lenient().when(queries.confirmedOrders(any())).thenReturn(List.of());
        lenient().when(queries.capacity(any())).thenReturn(100L);
        lenient().when(queries.prizes(any())).thenReturn(List.of(
                new Summaries.PrizeTier(70L, 1, "Gold", 1, new BigDecimal("100"), new BigDecimal("100"))));
        lenient().when(queries.payoutTotals(any())).thenReturn(List.of(
                new Summaries.PayoutTotals(PayoutStatus.PENDING, 1, new BigDecimal("100"))));
        lenient().when(queries.eventCountsByStatus(any())).thenReturn(Map.of(EventStatus.OPEN, 2L, EventStatus.DRAWN, 1L));
    }

    private ReportScope capturedScope() {
        ArgumentCaptor<ReportScope> captor = ArgumentCaptor.forClass(ReportScope.class);
        verify(queries, org.mockito.Mockito.atLeastOnce()).orderTotals(captor.capture());
        return captor.getValue();
    }

    @Test
    void organizerIsForcedToOwnTenant() {
        service.sales(new ReportRequest(null, null, null, null), ORGANIZER);
        assertThat(capturedScope().tenantId()).isEqualTo(10L);
    }

    @Test
    void organizerCannotRequestAnotherTenant() {
        assertThatThrownBy(() -> service.sales(new ReportRequest(20L, null, null, null), ORGANIZER))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(queries);
    }

    @Test
    void organizerCannotSeeAnotherTenantsEvent() {
        assertThatThrownBy(() -> service.tickets(ReportRequest.of(null, 5L), OTHER_ORGANIZER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.eventSummary(5L, OTHER_ORGANIZER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminMayNameTenantOrGoPlatformWide() {
        service.sales(new ReportRequest(null, null, null, null), ADMIN);
        assertThat(capturedScope().tenantId()).isNull();
    }

    @Test
    void adminAskingForEventOfOtherTenantIs404() {
        assertThatThrownBy(() -> service.sales(new ReportRequest(20L, 5L, null, null), ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void studentsServicesAndTenantlessOrganizersCannotReadReports() {
        assertThatThrownBy(() -> service.overview(ReportRequest.of(null, null), STUDENT)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.payouts(ReportRequest.of(null, null), SERVICE)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.prizes(ReportRequest.of(null, null), ORGANIZER_WITHOUT_TENANT))
                .hasMessageContaining("tenantId");
    }

    @Test
    void invalidPeriodIs400() {
        assertThatThrownBy(() -> service.sales(new ReportRequest(null, null, T0, T0), ORGANIZER))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void eventSummaryCombinesAllFigures() {
        Summaries.EventSummary s = service.eventSummary(5L, ORGANIZER);

        assertThat(s.name()).isEqualTo("Losar Raffle");
        assertThat(s.sales().revenue()).isEqualByComparingTo("150.00");
        assertThat(s.tickets().capacity()).isEqualTo(100);
        assertThat(s.tickets().sold()).isEqualTo(3);
        assertThat(s.tickets().reserved()).isEqualTo(1);
        assertThat(s.prizes().tiers()).hasSize(1);
        assertThat(s.payouts().outstandingAmount()).isEqualByComparingTo("100.00");
        assertThat(s.netAfterPrizes()).isEqualByComparingTo("50.00");
        assertThat(capturedScope()).isEqualTo(new ReportScope(10L, 5L, null, null));
    }

    @Test
    void overviewCountsEvents() {
        Summaries.Overview o = service.overview(ReportRequest.of(null, null), ORGANIZER);
        assertThat(o.events()).isEqualTo(3);
        assertThat(o.tenantId()).isEqualTo(10L);
        assertThat(o.prizes().tiers()).isEmpty();
    }

    @Test
    void prizeTiersOnlyForSingleEvent() {
        assertThat(service.prizes(ReportRequest.of(null, 5L), ORGANIZER).tiers()).hasSize(1);
        assertThat(service.prizes(ReportRequest.of(null, null), ORGANIZER).tiers()).isEmpty();
        assertThat(service.payouts(ReportRequest.of(null, 5L), ORGANIZER).winners()).isEqualTo(1);
    }

    @Test
    void eventsListAddsSalesPerEvent() {
        EventProjection e = event();
        when(queries.events(10L, EventStatus.OPEN, 0, 20)).thenReturn(new PageResult<>(List.of(e), 0, 20, 1));
        when(queries.confirmedTotalsByEvent(anyCollection())).thenReturn(
                Map.of(5L, new StatusTotals(OrderStatus.CONFIRMED, 2, 25, new BigDecimal("1250"))));

        PageResult<Summaries.EventRow> page = service.events(null, EventStatus.OPEN, 0, 20, ORGANIZER);

        assertThat(page.content().get(0).ticketsSold()).isEqualTo(25);
        assertThat(page.content().get(0).sellThroughPercent()).isEqualByComparingTo("25.00");
    }

    @Test
    void eventWithoutSalesShowsZero() {
        when(queries.events(null, null, 0, 20)).thenReturn(new PageResult<>(List.of(event()), 0, 20, 1));
        when(queries.confirmedTotalsByEvent(anyCollection())).thenReturn(Map.of());
        assertThat(service.events(null, null, 0, 20, ADMIN).content().get(0).revenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void studentSeesOwnPurchases() {
        when(queries.buyerTotals(100L)).thenReturn(Map.of(5L, Map.of(
                OrderStatus.CONFIRMED, new StatusTotals(OrderStatus.CONFIRMED, 2, 3, new BigDecimal("150")),
                OrderStatus.AWAITING_PAYMENT, new StatusTotals(OrderStatus.AWAITING_PAYMENT, 1, 1, new BigDecimal("50")),
                OrderStatus.FAILED, new StatusTotals(OrderStatus.FAILED, 1, 1, new BigDecimal("50")))));
        when(queries.eventNames(anyCollection())).thenReturn(Map.of(5L, "Losar Raffle"));

        Summaries.MyPurchases mine = service.myPurchases(STUDENT);

        assertThat(mine.buyerId()).isEqualTo(100L);
        assertThat(mine.confirmedTickets()).isEqualTo(3);
        assertThat(mine.totalSpent()).isEqualByComparingTo("150.00");
        assertThat(mine.events().get(0).pendingOrders()).isEqualTo(1);
        assertThat(mine.events().get(0).failedOrders()).isEqualTo(1);
        assertThat(mine.events().get(0).eventName()).isEqualTo("Losar Raffle");
    }

    @Test
    void studentWithOnlyPendingOrders() {
        when(queries.buyerTotals(100L)).thenReturn(Map.of(5L, Map.of(
                OrderStatus.AWAITING_PAYMENT, new StatusTotals(OrderStatus.AWAITING_PAYMENT, 1, 1, new BigDecimal("50")))));
        when(queries.eventNames(anyCollection())).thenReturn(Map.of());
        assertThat(service.myPurchases(STUDENT).totalSpent()).isEqualByComparingTo("0.00");
    }

    @Test
    void onlyStudentsHavePurchaseReports() {
        assertThatThrownBy(() -> service.myPurchases(ORGANIZER)).isInstanceOf(ForbiddenOperationException.class);
    }
}
