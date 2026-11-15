package com.yongdrel.reportingservice.domain;

import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.Summaries.StatusTotals;
import com.yongdrel.reportingservice.domain.service.ReportCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmedRevenueTest {
    @Test
    void excludesPendingAndFailedAmountsFromRevenue() {
        var sales = ReportCalculator.sales(List.of(
                new StatusTotals(OrderStatus.CONFIRMED, 2, 3, new BigDecimal("30.00")),
                new StatusTotals(OrderStatus.AWAITING_VERIFICATION, 1, 1, new BigDecimal("10.00")),
                new StatusTotals(OrderStatus.FAILED, 1, 1, new BigDecimal("20.00"))), List.of());

        assertThat(sales.confirmedOrders()).isEqualTo(2);
        assertThat(sales.ticketsSold()).isEqualTo(3);
        assertThat(sales.revenue()).isEqualByComparingTo("30.00");
        assertThat(sales.pendingAmount()).isEqualByComparingTo("10.00");
        assertThat(sales.failedOrders()).isEqualTo(1);
    }
}
