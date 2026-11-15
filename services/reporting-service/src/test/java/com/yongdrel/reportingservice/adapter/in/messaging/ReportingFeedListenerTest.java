package com.yongdrel.reportingservice.adapter.in.messaging;

import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.EventIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.OrderIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.PayoutIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.PrizeIngest;
import com.yongdrel.reportingservice.application.exception.ReferenceValidationException;
import com.yongdrel.reportingservice.application.port.in.IngestUseCase;
import com.yongdrel.reportingservice.domain.exception.StaleUpdateException;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.PayoutStatus;
import com.yongdrel.reportingservice.domain.model.UpsertOutcome;

import jakarta.validation.Validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportingFeedListenerTest {

    private static final Instant T = Instant.parse("2026-11-01T08:00:00Z");
    private IngestUseCase ingest;
    private ReportingFeedListener listener;

    @BeforeEach
    void setUp() {
        ingest = mock(IngestUseCase.class);
        listener = new ReportingFeedListener(ingest, Validation.buildDefaultValidatorFactory().getValidator());
    }

    private static EventIngest event(Long id) {
        return new EventIngest(id, 10L, "Losar Raffle", new BigDecimal("50"), 100, EventStatus.OPEN, null, T);
    }

    @Test
    void appliesAllFourFeedsWithoutCaller() {
        when(ingest.upsertEvent(any(), isNull())).thenReturn(UpsertOutcome.CREATED);
        when(ingest.upsertOrder(any(), isNull())).thenReturn(UpsertOutcome.CREATED);
        when(ingest.upsertPrize(any(), isNull())).thenReturn(UpsertOutcome.CREATED);
        when(ingest.upsertPayout(any(), isNull())).thenReturn(UpsertOutcome.CREATED);

        listener.onEvent(event(5L));
        listener.onOrder(new OrderIngest(1L, 5L, 10L, 100L, 1, new BigDecimal("50"), OrderStatus.AWAITING_PAYMENT, T, null, T));
        listener.onPrize(new PrizeIngest(70L, 5L, 10L, 1, "Gold", 1, new BigDecimal("500"), T));
        listener.onPayout(new PayoutIngest(90L, 5L, 10L, 70L, 100L, new BigDecimal("500"), PayoutStatus.PENDING, null, T));

        verify(ingest).upsertEvent(any(), isNull());
        verify(ingest).upsertPayout(any(), isNull());
    }

    @Test
    void staleMessagesAreAcknowledgedAndIgnored() {
        when(ingest.upsertEvent(any(), isNull())).thenThrow(new StaleUpdateException("older"));
        assertThatCode(() -> listener.onEvent(event(5L))).doesNotThrowAnyException();
    }

    @Test
    void invalidMessagesAreDeadLettered() {
        assertThatThrownBy(() -> listener.onEvent(event(null))).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("id is required");
        assertThatThrownBy(() -> listener.onEvent(new EventIngest(5L, 10L, "", new BigDecimal("50"), 100,
                EventStatus.OPEN, null, T))).isInstanceOf(AmqpRejectAndDontRequeueException.class).hasMessageContaining("name");
        verifyNoInteractions(ingest);
    }

    @Test
    void domainAndReferenceFailuresAreDeadLettered() {
        assertThatThrownBy(() -> listener.onOrder(new OrderIngest(1L, 5L, 10L, 100L, 1, new BigDecimal("50"),
                OrderStatus.CONFIRMED, T, null, T))).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("confirmedAt");

        when(ingest.upsertPrize(any(), isNull())).thenThrow(new ReferenceValidationException("unknown event"));
        assertThatThrownBy(() -> listener.onPrize(new PrizeIngest(70L, 5L, 10L, 1, "Gold", 1, new BigDecimal("500"), T)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
