package com.yongdrel.reportingservice.domain;

import com.yongdrel.reportingservice.domain.exception.InvalidProjectionException;
import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.OrderProjection;
import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.PayoutProjection;
import com.yongdrel.reportingservice.domain.model.PayoutStatus;
import com.yongdrel.reportingservice.domain.model.PrizeProjection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static com.yongdrel.reportingservice.TestData.T0;
import static com.yongdrel.reportingservice.TestData.event;
import static com.yongdrel.reportingservice.TestData.order;
import static com.yongdrel.reportingservice.TestData.payout;
import static com.yongdrel.reportingservice.TestData.prize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectionTest {

    @Test
    void moneyAndTimeAreNormalizedSoEqualityIsStable() {
        EventProjection a = new EventProjection(5L, 10L, "  Losar Raffle ", new BigDecimal("50"), 100, EventStatus.OPEN,
                null, T0.plusNanos(1_234_567));
        EventProjection b = new EventProjection(5L, 10L, "Losar Raffle", new BigDecimal("50.00"), 100, EventStatus.OPEN,
                null, T0.plusNanos(1_234_000));
        assertThat(a).isEqualTo(b);
        assertThat(a.ticketPrice().scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "event field {0} invalid")
    @ValueSource(strings = {"id", "tenant", "name", "longName", "price", "negativePrice", "fractionPrice", "tickets0",
            "ticketsMax", "status", "version"})
    void invalidEvents(String which) {
        assertThatThrownBy(() -> new EventProjection(
                which.equals("id") ? 0L : 5L,
                which.equals("tenant") ? null : 10L,
                which.equals("name") ? " " : which.equals("longName") ? "x".repeat(121) : "Raffle",
                which.equals("price") ? null : which.equals("negativePrice") ? new BigDecimal("-1")
                        : which.equals("fractionPrice") ? new BigDecimal("1.001") : BigDecimal.TEN,
                which.equals("tickets0") ? 0 : which.equals("ticketsMax") ? 1_000_001 : 10,
                which.equals("status") ? null : EventStatus.OPEN, null,
                which.equals("version") ? null : T0)).isInstanceOf(InvalidProjectionException.class);
    }

    @Test
    void confirmedOrderNeedsConfirmedAt() {
        assertThatThrownBy(() -> new OrderProjection(1L, 5L, 10L, 100L, 1, BigDecimal.TEN, OrderStatus.CONFIRMED,
                T0, null, T0)).hasMessageContaining("confirmedAt");
        assertThatThrownBy(() -> new OrderProjection(1L, 5L, 10L, 100L, 1, BigDecimal.TEN, OrderStatus.FAILED,
                T0, T0, T0)).hasMessageContaining("confirmedAt");
        assertThatThrownBy(() -> new OrderProjection(1L, 5L, 10L, 100L, 1, BigDecimal.TEN, OrderStatus.CONFIRMED,
                T0, T0.minusSeconds(1), T0)).hasMessageContaining("before createdAt");
    }

    @ParameterizedTest(name = "quantity {0} valid={1}")
    @CsvSource({"0,false", "1,true", "100,true", "101,false"})
    void orderQuantityBoundaries(int qty, boolean valid) {
        if (valid) {
            assertThat(new OrderProjection(1L, 5L, 10L, 100L, qty, BigDecimal.TEN, OrderStatus.AWAITING_PAYMENT,
                    T0, null, T0).quantity()).isEqualTo(qty);
        } else {
            assertThatThrownBy(() -> new OrderProjection(1L, 5L, 10L, 100L, qty, BigDecimal.TEN,
                    OrderStatus.AWAITING_PAYMENT, T0, null, T0)).isInstanceOf(InvalidProjectionException.class);
        }
    }

    @Test
    void orderRequiredFields() {
        assertThatThrownBy(() -> new OrderProjection(1L, 5L, 10L, null, 1, BigDecimal.TEN, OrderStatus.FAILED, T0, null, T0))
                .hasMessageContaining("buyerId");
        assertThatThrownBy(() -> new OrderProjection(1L, 5L, 10L, 1L, 1, BigDecimal.TEN, null, T0, null, T0))
                .hasMessageContaining("status");
        assertThatThrownBy(() -> new OrderProjection(1L, 5L, 10L, 1L, 1, BigDecimal.TEN, OrderStatus.FAILED, null, null, T0))
                .hasMessageContaining("createdAt");
    }

    @Test
    void prizeAndPayoutRules() {
        assertThat(prize().totalValue()).isEqualByComparingTo("1000");
        assertThatThrownBy(() -> new PrizeProjection(70L, 5L, 10L, 51, "x", 1, BigDecimal.ONE, T0)).hasMessageContaining("tier");
        assertThatThrownBy(() -> new PrizeProjection(70L, 5L, 10L, 1, "x", 1001, BigDecimal.ONE, T0)).hasMessageContaining("quantity");
        assertThatThrownBy(() -> new PayoutProjection(90L, 5L, 10L, 70L, 100L, BigDecimal.ONE, PayoutStatus.PAID, null, T0))
                .hasMessageContaining("paidAt");
        assertThatThrownBy(() -> new PayoutProjection(90L, 5L, 10L, 70L, 100L, BigDecimal.ONE, PayoutStatus.PENDING, T0, T0))
                .hasMessageContaining("paidAt");
        assertThat(payout(PayoutStatus.PAID).paidAt()).isNotNull();
        assertThat(order(1, OrderStatus.CONFIRMED, 2).amount()).isEqualByComparingTo("100");
        assertThat(event().salesCloseAt()).isAfter(T0);
        assertThat(Instant.EPOCH).isBefore(T0);
    }
}
