package com.yongdrel.reportingservice.application.service;

import com.yongdrel.reportingservice.application.exception.ForbiddenOperationException;
import com.yongdrel.reportingservice.application.exception.ReferenceValidationException;
import com.yongdrel.reportingservice.application.port.out.ProjectionRepositoryPort;
import com.yongdrel.reportingservice.domain.exception.StaleUpdateException;
import com.yongdrel.reportingservice.domain.model.OrderProjection;
import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.PayoutProjection;
import com.yongdrel.reportingservice.domain.model.PayoutStatus;
import com.yongdrel.reportingservice.domain.model.PrizeProjection;
import com.yongdrel.reportingservice.domain.model.UpsertOutcome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.yongdrel.reportingservice.TestData.ADMIN;
import static com.yongdrel.reportingservice.TestData.ORGANIZER;
import static com.yongdrel.reportingservice.TestData.SERVICE;
import static com.yongdrel.reportingservice.TestData.STUDENT;
import static com.yongdrel.reportingservice.TestData.T0;
import static com.yongdrel.reportingservice.TestData.event;
import static com.yongdrel.reportingservice.TestData.order;
import static com.yongdrel.reportingservice.TestData.payout;
import static com.yongdrel.reportingservice.TestData.prize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock ProjectionRepositoryPort projections;
    private IngestService service;

    @BeforeEach
    void setUp() {
        service = new IngestService(projections);
        lenient().when(projections.findEvent(5L)).thenReturn(Optional.of(event()));
        lenient().when(projections.findEvent(6L)).thenReturn(Optional.empty());
        lenient().when(projections.findOrder(anyLong())).thenReturn(Optional.empty());
        lenient().when(projections.findPrize(anyLong())).thenReturn(Optional.empty());
        lenient().when(projections.findPayout(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void eventCreatedThenUpdatedThenUnchanged() {
        when(projections.findEvent(5L)).thenReturn(Optional.empty());
        assertThat(service.upsertEvent(event(), SERVICE)).isEqualTo(UpsertOutcome.CREATED);

        when(projections.findEvent(5L)).thenReturn(Optional.of(event(T0)));
        assertThat(service.upsertEvent(event(T0.plusSeconds(1)), ADMIN)).isEqualTo(UpsertOutcome.UPDATED);
        assertThat(service.upsertEvent(event(T0), null)).isEqualTo(UpsertOutcome.UNCHANGED);
        verify(projections, org.mockito.Mockito.times(2)).save(any(com.yongdrel.reportingservice.domain.model.EventProjection.class));
    }

    @Test
    void staleEventIsRejected() {
        assertThatThrownBy(() -> service.upsertEvent(event(T0.minusSeconds(1)), SERVICE))
                .isInstanceOf(StaleUpdateException.class);
    }

    @Test
    void onlyServicesAndAdminsCanIngest() {
        assertThatThrownBy(() -> service.upsertEvent(event(), ORGANIZER)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.upsertOrder(order(1, OrderStatus.CONFIRMED, 1), STUDENT))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(projections);
    }

    @Test
    void orderNeedsKnownEventOfSameTenant() {
        assertThat(service.upsertOrder(order(1, OrderStatus.CONFIRMED, 2), SERVICE)).isEqualTo(UpsertOutcome.CREATED);

        OrderProjection unknownEvent = new OrderProjection(2L, 6L, 10L, 100L, 1, BigDecimal.TEN,
                OrderStatus.AWAITING_PAYMENT, T0, null, T0);
        assertThatThrownBy(() -> service.upsertOrder(unknownEvent, SERVICE))
                .isInstanceOf(ReferenceValidationException.class).hasMessageContaining("unknown event 6");

        OrderProjection wrongTenant = new OrderProjection(3L, 5L, 20L, 100L, 1, BigDecimal.TEN,
                OrderStatus.AWAITING_PAYMENT, T0, null, T0);
        assertThatThrownBy(() -> service.upsertOrder(wrongTenant, SERVICE))
                .isInstanceOf(ReferenceValidationException.class).hasMessageContaining("belongs to tenant 10");
    }

    @Test
    void orderCannotMoveToAnotherBuyer() {
        OrderProjection stored = order(1, OrderStatus.AWAITING_PAYMENT, 1);
        when(projections.findOrder(1L)).thenReturn(Optional.of(stored));
        OrderProjection otherBuyer = new OrderProjection(1L, 5L, 10L, 999L, 1, stored.amount(), stored.status(),
                stored.createdAt(), null, stored.sourceUpdatedAt().plusSeconds(1));

        assertThatThrownBy(() -> service.upsertOrder(otherBuyer, SERVICE)).isInstanceOf(StaleUpdateException.class);
        verify(projections, never()).save(any(OrderProjection.class));
    }

    @Test
    void prizeNeedsKnownEvent() {
        assertThat(service.upsertPrize(prize(), SERVICE)).isEqualTo(UpsertOutcome.CREATED);
        PrizeProjection orphan = new PrizeProjection(71L, 6L, 10L, 1, "x", 1, BigDecimal.ONE, T0);
        assertThatThrownBy(() -> service.upsertPrize(orphan, SERVICE)).isInstanceOf(ReferenceValidationException.class);
    }

    @Test
    void payoutNeedsPrizeOfSameEvent() {
        assertThatThrownBy(() -> service.upsertPayout(payout(PayoutStatus.PENDING), SERVICE))
                .isInstanceOf(ReferenceValidationException.class).hasMessageContaining("unknown prize 70");

        when(projections.findPrize(70L)).thenReturn(Optional.of(prize()));
        assertThat(service.upsertPayout(payout(PayoutStatus.PENDING), SERVICE)).isEqualTo(UpsertOutcome.CREATED);

        when(projections.findPrize(70L)).thenReturn(Optional.of(new PrizeProjection(70L, 7L, 10L, 1, "x", 1,
                BigDecimal.ONE, T0)));
        assertThatThrownBy(() -> service.upsertPayout(payout(PayoutStatus.PENDING), SERVICE))
                .hasMessageContaining("belongs to event 7");
    }

    @Test
    void payoutStatusProgressesWithNewerVersion() {
        when(projections.findPrize(70L)).thenReturn(Optional.of(prize()));
        when(projections.findPayout(90L)).thenReturn(Optional.of(payout(PayoutStatus.PENDING)));
        PayoutProjection pending = payout(PayoutStatus.PENDING);
        PayoutProjection paid = new PayoutProjection(90L, 5L, 10L, 70L, 100L, pending.amount(), PayoutStatus.PAID,
                T0.plusSeconds(9000), pending.sourceUpdatedAt().plusSeconds(60));

        assertThat(service.upsertPayout(paid, SERVICE)).isEqualTo(UpsertOutcome.UPDATED);
    }
}
