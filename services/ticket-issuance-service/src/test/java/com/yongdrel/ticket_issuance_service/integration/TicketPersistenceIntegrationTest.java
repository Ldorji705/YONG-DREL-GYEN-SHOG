
package com.yongdrel.ticket_issuance_service.integration;

import com.yongdrel.ticket_issuance_service.adapter.out.event.EventServiceAdapter;
import com.yongdrel.ticket_issuance_service.adapter.out.persistence.SpringDataTicketRepository;
import com.yongdrel.ticket_issuance_service.application.service.TicketIssuanceService;
import com.yongdrel.ticket_issuance_service.dto.IssueTicketRequest;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.payment.listener.enabled=false",
        "app.outbox.dispatcher.enabled=false"
})
@Transactional
class TicketPersistenceIntegrationTest {

    @Autowired
    private TicketIssuanceService service;

    @Autowired
    private SpringDataTicketRepository ticketRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    // Mock only the external Event Service
    @MockitoBean
    private EventServiceAdapter eventServiceAdapter;

    @Test
    void shouldPersistTicketsAndOutboxWithoutDuplicates() {

        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Simulate an ACTIVE event that closes tomorrow
        when(eventServiceAdapter.getEvent(eventId))
                .thenReturn(new EventServiceAdapter.EventInfo(
                        eventId,
                        "ACTIVE",
                        LocalDateTime.now(
                                ZoneId.of("Asia/Thimphu")
                        ).plusDays(1)
                ));

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 3);

        long initialOutboxCount = outboxRepository.count();

        // First issuance
        var firstResult = service.issueTickets(request);

        assertEquals(3, firstResult.size());

        assertEquals(
                3,
                ticketRepository.findByOrderId(orderId).size()
        );

        assertEquals(
                initialOutboxCount + 1,
                outboxRepository.count()
        );

        // Retry the same order
        var secondResult = service.issueTickets(request);

        assertEquals(3, secondResult.size());

        assertEquals(
                3,
                ticketRepository.findByOrderId(orderId).size()
        );

        assertEquals(
                initialOutboxCount + 1,
                outboxRepository.count()
        );

        assertEquals(
                firstResult.stream()
                        .map(t -> t.id())
                        .toList(),
                secondResult.stream()
                        .map(t -> t.id())
                        .toList()
        );

        // Event Service should only be called once.
        // Retrying an existing order must not require it.
        verify(eventServiceAdapter, times(1))
                .getEvent(eventId);
    }
}
