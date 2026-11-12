
package com.yongdrel.ticket_issuance_service.application.service;

import com.yongdrel.ticket_issuance_service.adapter.out.event.EventServiceAdapter;
import com.yongdrel.ticket_issuance_service.adapter.out.persistence.SpringDataTicketRepository;
import com.yongdrel.ticket_issuance_service.adapter.out.persistence.TicketEntity;
import com.yongdrel.ticket_issuance_service.domain.model.TicketStatus;
import com.yongdrel.ticket_issuance_service.dto.IssueTicketRequest;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketIssuanceServiceTest {

    private static final ZoneId BHUTAN_ZONE =
            ZoneId.of("Asia/Thimphu");

    @Mock
    private SpringDataTicketRepository repository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private EventServiceAdapter eventServiceAdapter;

    private TicketIssuanceService service;

    private UUID orderId;
    private UUID eventId;
    private Long userId;

    @BeforeEach
    void setUp() {

        service = new TicketIssuanceService(
                repository,
                outboxRepository,
                JsonMapper.builder()
                        .findAndAddModules()
                        .build(),
                eventServiceAdapter
        );

        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        userId = 1L;
    }

    // TEST 1: Issue three unique tickets
    @Test
    void shouldIssueThreeUniqueTicketsAndCreateOutboxEvent() {

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of());

        when(eventServiceAdapter.getEvent(eventId))
                .thenReturn(activeEvent());

        when(repository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<TicketEntity> tickets =
                            invocation.getArgument(0);
                    return tickets;
                });

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 3);

        var result = service.issueTickets(request);

        assertEquals(3, result.size());

        assertEquals(3,
                result.stream()
                        .map(ticket -> ticket.ticketNumber())
                        .distinct()
                        .count());

        result.forEach(ticket -> {
            assertEquals(orderId, ticket.orderId());
            assertEquals(eventId, ticket.eventId());
            assertEquals(userId, ticket.userId());
            assertEquals("ISSUED", ticket.status());
            assertNotNull(ticket.issuedAt());
        });

        verify(eventServiceAdapter).getEvent(eventId);
        verify(repository, times(1)).saveAllAndFlush(any());
        verify(outboxRepository, times(1)).save(any());
    }

    // TEST 2: Prevent duplicate ticket issuance
    @Test
    void shouldReturnExistingTicketsWithoutCreatingDuplicates() {

        TicketEntity existing = createTicket();

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of(existing));

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 1);

        var result = service.issueTickets(request);

        assertEquals(1, result.size());
        assertEquals(existing.getId(), result.get(0).id());

        verifyNoInteractions(eventServiceAdapter);
        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    // TEST 3: Reject different quantity for existing order
    @Test
    void shouldRejectDifferentQuantityForExistingOrder() {

        TicketEntity existing = createTicket();

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of(existing));

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 2);

        assertThrows(
                IllegalStateException.class,
                () -> service.issueTickets(request)
        );

        verifyNoInteractions(eventServiceAdapter);
        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    // TEST 4: Reject different user for existing order
    @Test
    void shouldRejectDifferentUserForExistingOrder() {

        TicketEntity existing = createTicket();

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of(existing));

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, 2L, 1);

        assertThrows(
                IllegalStateException.class,
                () -> service.issueTickets(request)
        );

        verifyNoInteractions(eventServiceAdapter);
        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    // TEST 5: Reject different event for existing order
    @Test
    void shouldRejectDifferentEventForExistingOrder() {

        TicketEntity existing = createTicket();

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of(existing));

        IssueTicketRequest request =
                new IssueTicketRequest(
                        orderId,
                        UUID.randomUUID(),
                        userId,
                        1
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.issueTickets(request)
        );

        verifyNoInteractions(eventServiceAdapter);
        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    // TEST 6: Reject inactive events
    @Test
    void shouldRejectInactiveEvent() {

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of());

        when(eventServiceAdapter.getEvent(eventId))
                .thenReturn(new EventServiceAdapter.EventInfo(
                        eventId,
                        "DRAFT",
                        LocalDateTime.now(BHUTAN_ZONE).plusDays(1)
                ));

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.issueTickets(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    // TEST 7: Reject expired events
    @Test
    void shouldRejectExpiredEvent() {

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of());

        when(eventServiceAdapter.getEvent(eventId))
                .thenReturn(new EventServiceAdapter.EventInfo(
                        eventId,
                        "ACTIVE",
                        LocalDateTime.now(BHUTAN_ZONE).minusDays(1)
                ));

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.issueTickets(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    // TEST 8: Reject an event whose deadline is now
    @Test
    void shouldRejectEventAtDeadline() {

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of());

        when(eventServiceAdapter.getEvent(eventId))
                .thenReturn(new EventServiceAdapter.EventInfo(
                        eventId,
                        "ACTIVE",
                        LocalDateTime.now(BHUTAN_ZONE)
                ));

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.issueTickets(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    // TEST 9: Reject issuance if Event Service fails
    @Test
    void shouldRejectWhenEventServiceUnavailable() {

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of());

        when(eventServiceAdapter.getEvent(eventId))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Unable to retrieve event information"
                ));

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.issueTickets(request)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());

        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    // Helper: Create an active event
    private EventServiceAdapter.EventInfo activeEvent() {

        return new EventServiceAdapter.EventInfo(
                eventId,
                "ACTIVE",
                LocalDateTime.now(BHUTAN_ZONE).plusDays(1)
        );
    }

    // Helper: Create an existing ticket
    private TicketEntity createTicket() {

        TicketEntity ticket = new TicketEntity();

        ticket.setId(UUID.randomUUID());
        ticket.setTicketNumber("YG-TEST-" + UUID.randomUUID());
        ticket.setOrderId(orderId);
        ticket.setEventId(eventId);
        ticket.setUserId(userId);
        ticket.setTicketIndex(1);
        ticket.setStatus(TicketStatus.ISSUED);
        ticket.setIssuedAt(Instant.now());

        return ticket;
    }
}
