package com.yongdrel.reportingservice;

import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.OrderProjection;
import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.PayoutProjection;
import com.yongdrel.reportingservice.domain.model.PayoutStatus;
import com.yongdrel.reportingservice.domain.model.PrizeProjection;
import com.yongdrel.reportingservice.domain.model.Role;

import java.math.BigDecimal;
import java.time.Instant;

/** Tenant 10 owns event 5 (100 tickets at 50.00). Tenant 20 is someone else. */
public final class TestData {

    public static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");

    public static final Caller ADMIN = new Caller(1L, Role.ADMIN, null);
    public static final Caller SERVICE = new Caller(2L, Role.SERVICE, null);
    public static final Caller ORGANIZER = new Caller(3L, Role.ORGANIZER, 10L);
    public static final Caller COLLEAGUE = new Caller(6L, Role.ORGANIZER, 10L);
    public static final Caller OTHER_ORGANIZER = new Caller(4L, Role.ORGANIZER, 20L);
    public static final Caller ORGANIZER_WITHOUT_TENANT = new Caller(5L, Role.ORGANIZER, null);
    public static final Caller STUDENT = new Caller(100L, Role.STUDENT, null);

    private TestData() {
    }

    public static EventProjection event() {
        return event(T0);
    }

    public static EventProjection event(Instant version) {
        return new EventProjection(5L, 10L, "Losar Raffle", new BigDecimal("50"), 100, EventStatus.OPEN,
                T0.plusSeconds(86_400 * 7), version);
    }

    public static OrderProjection order(long id, OrderStatus status, int qty) {
        Instant created = T0.plusSeconds(3600);
        return new OrderProjection(id, 5L, 10L, 100L, qty, new BigDecimal("50").multiply(BigDecimal.valueOf(qty)),
                status, created, status == OrderStatus.CONFIRMED ? created.plusSeconds(600) : null, created.plusSeconds(600));
    }

    public static PrizeProjection prize() {
        return new PrizeProjection(70L, 5L, 10L, 1, "Gold coin", 2, new BigDecimal("500"), T0);
    }

    public static PayoutProjection payout(PayoutStatus status) {
        return new PayoutProjection(90L, 5L, 10L, 70L, 100L, new BigDecimal("500"), status,
                status == PayoutStatus.PAID ? T0.plusSeconds(7200) : null, T0.plusSeconds(7200));
    }
}
