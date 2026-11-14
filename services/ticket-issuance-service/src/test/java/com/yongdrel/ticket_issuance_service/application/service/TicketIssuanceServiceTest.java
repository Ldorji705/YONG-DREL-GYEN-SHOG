package com.yongdrel.ticket_issuance_service.application.service;

import com.yongdrel.ticket_issuance_service.adapter.out.persistence.SpringDataTicketRepository;
import com.yongdrel.ticket_issuance_service.adapter.out.persistence.TicketEntity;
import com.yongdrel.ticket_issuance_service.dto.IssueTicketRequest;
import com.yongdrel.ticket_issuance_service.domain.model.TicketStatus;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketIssuanceServiceTest {

    @Mock
    private SpringDataTicketRepository repository;

    @Mock
    private OutboxEventRepository outboxRepository;

    private TicketIssuanceService service;

    private UUID orderId;
    private UUID eventId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new TicketIssuanceService(
                repository,
                outboxRepository,
                JsonMapper.builder().findAndAddModules().build()
        );

        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void shouldIssueThreeUniqueTicketsAndCreateOutboxEvent() {

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of());

        when(repository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<TicketEntity> tickets = invocation.getArgument(0);
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

        verify(repository, times(1)).saveAllAndFlush(any());
        verify(outboxRepository, times(1)).save(any());
    }

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

        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

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

        verify(repository, never()).saveAllAndFlush(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void shouldRejectDifferentUserForExistingOrder() {

        TicketEntity existing = createTicket();

        when(repository.findByOrderId(orderId))
                .thenReturn(List.of(existing));

        IssueTicketRequest request =
                new IssueTicketRequest(
                        orderId,
                        eventId,
                        UUID.randomUUID(),
                        1
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.issueTickets(request)
        );

        verify(outboxRepository, never()).save(any());
    }

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

        verify(outboxRepository, never()).save(any());
    }

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
