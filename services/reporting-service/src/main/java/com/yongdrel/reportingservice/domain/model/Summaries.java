package com.yongdrel.reportingservice.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Report result types. All money is in the platform currency (BTN), scale 2. */
public final class Summaries {

    private Summaries() {
    }

    /** Raw aggregate of orders in one status. */
    public record StatusTotals(OrderStatus status, long orders, long tickets, BigDecimal amount) {
    }

    public record DailySales(LocalDate date, long orders, long tickets, BigDecimal revenue) {
    }

    public record SalesSummary(
            String currency,
            long confirmedOrders,
            long ticketsSold,
            BigDecimal revenue,
            BigDecimal averageOrderValue,
            long pendingOrders,
            BigDecimal pendingAmount,
            long failedOrders,
            BigDecimal conversionRatePercent,
            List<DailySales> daily
    ) {
    }

    public record TicketSummary(
            long capacity,
            long sold,
            long reserved,
            long available,
            BigDecimal sellThroughPercent
    ) {
    }

    public record PrizeTier(Long prizeId, int tier, String name, int quantity, BigDecimal unitValue, BigDecimal totalValue) {
    }

    public record PrizeSummary(String currency, long prizeTiers, long prizeUnits, BigDecimal totalPrizeValue,
                               List<PrizeTier> tiers) {
    }

    public record PayoutTotals(PayoutStatus status, long count, BigDecimal amount) {
    }

    public record PayoutSummary(
            String currency,
            long winners,
            BigDecimal totalAmount,
            long paidCount,
            BigDecimal paidAmount,
            long pendingCount,
            BigDecimal outstandingAmount,
            long cancelledCount,
            BigDecimal paidPercent
    ) {
    }

    public record EventSummary(
            Long eventId,
            Long tenantId,
            String name,
            EventStatus status,
            BigDecimal ticketPrice,
            SalesSummary sales,
            TicketSummary tickets,
            PrizeSummary prizes,
            PayoutSummary payouts,
            BigDecimal netAfterPrizes
    ) {
    }

    public record Overview(
            Long tenantId,
            long events,
            Map<EventStatus, Long> eventsByStatus,
            SalesSummary sales,
            TicketSummary tickets,
            PrizeSummary prizes,
            PayoutSummary payouts,
            BigDecimal netAfterPrizes
    ) {
    }

    public record EventRow(Long eventId, String name, EventStatus status, BigDecimal ticketPrice, long capacity,
                           long ticketsSold, BigDecimal revenue, BigDecimal sellThroughPercent) {
    }

    public record PurchaseRow(Long eventId, String eventName, long confirmedTickets, BigDecimal spent,
                              long pendingOrders, long failedOrders) {
    }

    public record MyPurchases(Long buyerId, String currency, long confirmedTickets, BigDecimal totalSpent,
                              List<PurchaseRow> events) {
    }
}
