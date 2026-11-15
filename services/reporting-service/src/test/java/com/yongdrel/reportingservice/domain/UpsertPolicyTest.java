package com.yongdrel.reportingservice.domain;

import com.yongdrel.reportingservice.domain.exception.StaleUpdateException;
import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.OrderProjection;
import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.UpsertOutcome;
import com.yongdrel.reportingservice.domain.service.UpsertPolicy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.yongdrel.reportingservice.TestData.T0;
import static com.yongdrel.reportingservice.TestData.event;
import static com.yongdrel.reportingservice.TestData.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpsertPolicyTest {

    @Test
    void newRecordIsCreated() {
        assertThat(UpsertPolicy.decide(null, event(), "Event", EventProjection::tenantId)).isEqualTo(UpsertOutcome.CREATED);
    }

    @Test
    void newerVersionUpdates() {
        assertThat(UpsertPolicy.decide(event(T0), event(T0.plusSeconds(1)), "Event", EventProjection::tenantId))
                .isEqualTo(UpsertOutcome.UPDATED);
    }

    @Test
    void identicalRepeatIsUnchanged() {
        assertThat(UpsertPolicy.decide(event(T0), event(T0), "Event", EventProjection::tenantId))
                .isEqualTo(UpsertOutcome.UNCHANGED);
    }

    @Test
    void olderVersionIsStale() {
        assertThatThrownBy(() -> UpsertPolicy.decide(event(T0), event(T0.minusSeconds(1)), "Event", EventProjection::tenantId))
                .isInstanceOf(StaleUpdateException.class).hasMessageContaining("older than");
    }

    @Test
    void sameVersionDifferentContentConflicts() {
        EventProjection changed = new EventProjection(5L, 10L, "Renamed", new BigDecimal("50"), 100, EventStatus.OPEN,
                null, T0);
        assertThatThrownBy(() -> UpsertPolicy.decide(event(T0), changed, "Event", EventProjection::tenantId))
                .hasMessageContaining("in conflict with");
    }

    @Test
    void immutableReferenceCannotChange() {
        EventProjection moved = new EventProjection(5L, 20L, "Losar Raffle", new BigDecimal("50"), 100,
                EventStatus.OPEN, null, T0.plusSeconds(60));
        assertThatThrownBy(() -> UpsertPolicy.decide(event(T0), moved, "Event", EventProjection::tenantId))
                .hasMessageContaining("cannot change");

        OrderProjection existing = order(1, OrderStatus.AWAITING_PAYMENT, 1);
        OrderProjection otherBuyer = new OrderProjection(1L, 5L, 10L, 999L, 1, existing.amount(), existing.status(),
                existing.createdAt(), null, existing.sourceUpdatedAt().plusSeconds(1));
        assertThatThrownBy(() -> UpsertPolicy.decide(existing, otherBuyer, "Order", OrderProjection::buyerId))
                .isInstanceOf(StaleUpdateException.class);
    }
}
