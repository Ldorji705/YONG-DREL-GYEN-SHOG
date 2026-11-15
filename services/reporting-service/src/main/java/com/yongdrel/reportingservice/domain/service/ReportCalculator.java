package com.yongdrel.reportingservice.domain.service;

import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.PayoutStatus;
import com.yongdrel.reportingservice.domain.model.Summaries.DailySales;
import com.yongdrel.reportingservice.domain.model.Summaries.PayoutSummary;
import com.yongdrel.reportingservice.domain.model.Summaries.PayoutTotals;
import com.yongdrel.reportingservice.domain.model.Summaries.PrizeSummary;
import com.yongdrel.reportingservice.domain.model.Summaries.PrizeTier;
import com.yongdrel.reportingservice.domain.model.Summaries.SalesSummary;
import com.yongdrel.reportingservice.domain.model.Summaries.StatusTotals;
import com.yongdrel.reportingservice.domain.model.Summaries.TicketSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure calculations from raw aggregates to report figures. */
public final class ReportCalculator {

    public static final String CURRENCY = "BTN";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ReportCalculator() {
    }

    /** One row of a confirmed order, used for the daily breakdown. */
    public record OrderRow(Instant createdAt, int quantity, BigDecimal amount) {
    }

    public static SalesSummary sales(Collection<StatusTotals> totals, Collection<OrderRow> confirmedRows) {
        StatusTotals confirmed = find(totals, OrderStatus.CONFIRMED);
        long pendingOrders = 0;
        BigDecimal pendingAmount = money(BigDecimal.ZERO);
        for (StatusTotals t : totals) {
            if (t.status().isPending()) {
                pendingOrders += t.orders();
                pendingAmount = pendingAmount.add(t.amount());
            }
        }
        long failed = find(totals, OrderStatus.FAILED).orders();
        long decided = confirmed.orders() + failed;
        return new SalesSummary(CURRENCY, confirmed.orders(), confirmed.tickets(), money(confirmed.amount()),
                divide(confirmed.amount(), confirmed.orders()), pendingOrders, money(pendingAmount), failed,
                percent(confirmed.orders(), decided), daily(confirmedRows));
    }

    static List<DailySales> daily(Collection<OrderRow> rows) {
        Map<LocalDate, long[]> counts = new TreeMap<>();
        Map<LocalDate, BigDecimal> revenue = new TreeMap<>();
        for (OrderRow r : rows) {
            LocalDate day = LocalDate.ofInstant(r.createdAt(), ZoneOffset.UTC);
            long[] c = counts.computeIfAbsent(day, d -> new long[2]);
            c[0]++;
            c[1] += r.quantity();
            revenue.merge(day, r.amount(), BigDecimal::add);
        }
        return counts.entrySet().stream()
                .map(e -> new DailySales(e.getKey(), e.getValue()[0], e.getValue()[1], money(revenue.get(e.getKey()))))
                .toList();
    }

    public static TicketSummary tickets(long capacity, Collection<StatusTotals> totals) {
        long sold = find(totals, OrderStatus.CONFIRMED).tickets();
        long reserved = totals.stream().filter(t -> t.status().isPending()).mapToLong(StatusTotals::tickets).sum();
        long available = Math.max(0, capacity - sold - reserved);
        return new TicketSummary(capacity, sold, reserved, available, percent(sold, capacity));
    }

    public static PrizeSummary prizes(Collection<PrizeTier> tiers, boolean includeTiers) {
        long units = tiers.stream().mapToLong(PrizeTier::quantity).sum();
        BigDecimal value = tiers.stream().map(PrizeTier::totalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PrizeSummary(CURRENCY, tiers.size(), units, money(value), includeTiers ? List.copyOf(tiers) : List.of());
    }

    public static PayoutSummary payouts(Collection<PayoutTotals> totals) {
        PayoutTotals paid = findPayout(totals, PayoutStatus.PAID);
        PayoutTotals pending = findPayout(totals, PayoutStatus.PENDING);
        PayoutTotals cancelled = findPayout(totals, PayoutStatus.CANCELLED);
        BigDecimal owed = paid.amount().add(pending.amount());
        return new PayoutSummary(CURRENCY, paid.count() + pending.count(), money(owed), paid.count(),
                money(paid.amount()), pending.count(), money(pending.amount()), cancelled.count(),
                percentOf(paid.amount(), owed));
    }

    /** Confirmed revenue minus the value of prizes (what the organizer keeps). */
    public static BigDecimal netAfterPrizes(SalesSummary sales, PrizeSummary prizes) {
        return money(sales.revenue().subtract(prizes.totalPrizeValue()));
    }

    public static BigDecimal percent(long part, long whole) {
        return whole == 0 ? money(BigDecimal.ZERO)
                : BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? money(BigDecimal.ZERO)
                : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal divide(BigDecimal amount, long count) {
        return count == 0 ? money(BigDecimal.ZERO) : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static StatusTotals find(Collection<StatusTotals> totals, OrderStatus status) {
        return totals.stream().filter(t -> t.status() == status).findFirst()
                .orElse(new StatusTotals(status, 0, 0, BigDecimal.ZERO));
    }

    private static PayoutTotals findPayout(Collection<PayoutTotals> totals, PayoutStatus status) {
        return totals.stream().filter(t -> t.status() == status).findFirst()
                .orElse(new PayoutTotals(status, 0, BigDecimal.ZERO));
    }
}
