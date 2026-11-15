package com.yongdrel.reportingservice.domain;

import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.PayoutStatus;
import com.yongdrel.reportingservice.domain.model.Summaries.PayoutSummary;
import com.yongdrel.reportingservice.domain.model.Summaries.PayoutTotals;
import com.yongdrel.reportingservice.domain.model.Summaries.PrizeSummary;
import com.yongdrel.reportingservice.domain.model.Summaries.PrizeTier;
import com.yongdrel.reportingservice.domain.model.Summaries.SalesSummary;
import com.yongdrel.reportingservice.domain.model.Summaries.StatusTotals;
import com.yongdrel.reportingservice.domain.model.Summaries.TicketSummary;
import com.yongdrel.reportingservice.domain.service.ReportCalculator;
import com.yongdrel.reportingservice.domain.service.ReportCalculator.OrderRow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCalculatorTest {

    private static final List<StatusTotals> TOTALS = List.of(
            new StatusTotals(OrderStatus.CONFIRMED, 3, 5, new BigDecimal("250")),
            new StatusTotals(OrderStatus.AWAITING_PAYMENT, 1, 2, new BigDecimal("100")),
            new StatusTotals(OrderStatus.AWAITING_VERIFICATION, 2, 2, new BigDecimal("100")),
            new StatusTotals(OrderStatus.FAILED, 1, 1, new BigDecimal("50")));

    @Test
    void salesFigures() {
        List<OrderRow> rows = List.of(
                new OrderRow(Instant.parse("2026-11-01T23:59:59Z"), 2, new BigDecimal("100")),
                new OrderRow(Instant.parse("2026-11-02T00:00:00Z"), 1, new BigDecimal("50")),
                new OrderRow(Instant.parse("2026-11-02T10:00:00Z"), 2, new BigDecimal("100")));

        SalesSummary s = ReportCalculator.sales(TOTALS, rows);

        assertThat(s.currency()).isEqualTo("BTN");
        assertThat(s.confirmedOrders()).isEqualTo(3);
        assertThat(s.ticketsSold()).isEqualTo(5);
        assertThat(s.revenue()).isEqualByComparingTo("250.00");
        assertThat(s.averageOrderValue()).isEqualByComparingTo("83.33");
        assertThat(s.pendingOrders()).isEqualTo(3);
        assertThat(s.pendingAmount()).isEqualByComparingTo("200.00");
        assertThat(s.failedOrders()).isEqualTo(1);
        assertThat(s.conversionRatePercent()).isEqualByComparingTo("75.00");
        assertThat(s.daily()).hasSize(2);
        assertThat(s.daily().get(0).date()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(s.daily().get(1).tickets()).isEqualTo(3);
        assertThat(s.daily().get(1).revenue()).isEqualByComparingTo("150.00");
    }

    @Test
    void emptySalesHaveZeroesNotErrors() {
        SalesSummary s = ReportCalculator.sales(List.of(), List.of());
        assertThat(s.revenue()).isEqualByComparingTo("0.00");
        assertThat(s.averageOrderValue()).isEqualByComparingTo("0.00");
        assertThat(s.conversionRatePercent()).isEqualByComparingTo("0.00");
        assertThat(s.daily()).isEmpty();
    }

    @Test
    void ticketFigures() {
        TicketSummary t = ReportCalculator.tickets(100, TOTALS);
        assertThat(t.sold()).isEqualTo(5);
        assertThat(t.reserved()).isEqualTo(4);
        assertThat(t.available()).isEqualTo(91);
        assertThat(t.sellThroughPercent()).isEqualByComparingTo("5.00");
    }

    @Test
    void availableNeverNegative() {
        assertThat(ReportCalculator.tickets(3, TOTALS).available()).isZero();
        assertThat(ReportCalculator.tickets(0, List.of()).sellThroughPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void prizeFigures() {
        List<PrizeTier> tiers = List.of(
                new PrizeTier(1L, 1, "Gold", 1, new BigDecimal("1000"), new BigDecimal("1000")),
                new PrizeTier(2L, 2, "Silver", 3, new BigDecimal("100"), new BigDecimal("300")));
        PrizeSummary withTiers = ReportCalculator.prizes(tiers, true);
        assertThat(withTiers.prizeTiers()).isEqualTo(2);
        assertThat(withTiers.prizeUnits()).isEqualTo(4);
        assertThat(withTiers.totalPrizeValue()).isEqualByComparingTo("1300.00");
        assertThat(withTiers.tiers()).hasSize(2);
        assertThat(ReportCalculator.prizes(tiers, false).tiers()).isEmpty();
    }

    @Test
    void payoutFigures() {
        PayoutSummary p = ReportCalculator.payouts(List.of(
                new PayoutTotals(PayoutStatus.PAID, 2, new BigDecimal("600")),
                new PayoutTotals(PayoutStatus.PENDING, 1, new BigDecimal("200")),
                new PayoutTotals(PayoutStatus.CANCELLED, 1, new BigDecimal("50"))));
        assertThat(p.winners()).isEqualTo(3);
        assertThat(p.totalAmount()).isEqualByComparingTo("800.00");
        assertThat(p.paidAmount()).isEqualByComparingTo("600.00");
        assertThat(p.outstandingAmount()).isEqualByComparingTo("200.00");
        assertThat(p.cancelledCount()).isEqualTo(1);
        assertThat(p.paidPercent()).isEqualByComparingTo("75.00");
        assertThat(ReportCalculator.payouts(List.of()).paidPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void netAfterPrizes() {
        SalesSummary s = ReportCalculator.sales(TOTALS, List.of());
        PrizeSummary p = ReportCalculator.prizes(List.of(new PrizeTier(1L, 1, "Gold", 1, new BigDecimal("300"),
                new BigDecimal("300"))), false);
        assertThat(ReportCalculator.netAfterPrizes(s, p)).isEqualByComparingTo("-50.00");
    }

    @ParameterizedTest(name = "{0}/{1} = {2}%")
    @CsvSource({"0,0,0.00", "1,3,33.33", "2,3,66.67", "3,3,100.00"})
    void percentRounding(long part, long whole, String expected) {
        assertThat(ReportCalculator.percent(part, whole)).isEqualByComparingTo(expected);
    }
}
