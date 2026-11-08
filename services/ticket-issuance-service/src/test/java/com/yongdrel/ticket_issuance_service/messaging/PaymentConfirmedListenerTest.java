package com.yongdrel.ticket_issuance_service.messaging;

import com.yongdrel.ticket_issuance_service.application.service.TicketIssuanceService;
import com.yongdrel.ticket_issuance_service.dto.IssueTicketRequest;
import com.yongdrel.ticket_issuance_service.messaging.event.PaymentConfirmedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentConfirmedListenerTest {

    private TicketIssuanceService ticketService;
    private PaymentConfirmedListener listener;

    private UUID orderId;
    private UUID eventId;
    private Long userId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketIssuanceService.class);
        listener = new PaymentConfirmedListener(ticketService);

        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        userId = 1L;
        paymentId = UUID.randomUUID();
    }

    @Test
    void shouldIssueTicketsForValidPayment() {

        PaymentConfirmedEvent event = new PaymentConfirmedEvent(
                UUID.randomUUID(),
                paymentId,
                orderId,
                eventId,
                userId,
                3,
                Instant.now()
        );

        listener.handlePaymentConfirmed(event);

        verify(ticketService).issueTickets(
                new IssueTicketRequest(orderId, eventId, userId, 3)
        );
    }

    @Test
    void shouldRejectNullPaymentEvent() {

        assertThrows(
                IllegalArgumentException.class,
                () -> listener.handlePaymentConfirmed(null)
        );

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectZeroQuantity() {

        PaymentConfirmedEvent event = createEvent(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> listener.handlePaymentConfirmed(event)
        );

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectQuantityAboveLimit() {

        PaymentConfirmedEvent event = createEvent(101);

        assertThrows(
                IllegalArgumentException.class,
                () -> listener.handlePaymentConfirmed(event)
        );

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldRejectMissingOrderId() {

        PaymentConfirmedEvent event = new PaymentConfirmedEvent(
                UUID.randomUUID(),
                paymentId,
                null,
                eventId,
                userId,
                2,
                Instant.now()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> listener.handlePaymentConfirmed(event)
        );

        verifyNoInteractions(ticketService);
    }

    private PaymentConfirmedEvent createEvent(int quantity) {

        return new PaymentConfirmedEvent(
                UUID.randomUUID(),
                paymentId,
                orderId,
                eventId,
                userId,
                quantity,
                Instant.now()
        );
    }
}
