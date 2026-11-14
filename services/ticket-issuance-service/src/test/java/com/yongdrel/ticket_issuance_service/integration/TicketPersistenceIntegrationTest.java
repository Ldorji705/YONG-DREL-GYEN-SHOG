package com.yongdrel.ticket_issuance_service.integration;

import com.yongdrel.ticket_issuance_service.application.service.TicketIssuanceService;
import com.yongdrel.ticket_issuance_service.adapter.out.persistence.SpringDataTicketRepository;
import com.yongdrel.ticket_issuance_service.dto.IssueTicketRequest;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void shouldPersistTicketsAndOutboxWithoutDuplicates() {

        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        IssueTicketRequest request =
                new IssueTicketRequest(orderId, eventId, userId, 3);

        long initialOutboxCount = outboxRepository.count();

        // First issuance
        var firstResult = service.issueTickets(request);

        assertEquals(3, firstResult.size());

        assertEquals(3,
                ticketRepository.findByOrderId(orderId).size());

        assertEquals(initialOutboxCount + 1,
                outboxRepository.count());

        // Retry the same order
        var secondResult = service.issueTickets(request);

        assertEquals(3, secondResult.size());

        assertEquals(3,
                ticketRepository.findByOrderId(orderId).size());

        assertEquals(initialOutboxCount + 1,
                outboxRepository.count());

        assertEquals(
                firstResult.stream().map(t -> t.id()).toList(),
                secondResult.stream().map(t -> t.id()).toList()
        );
    }
}
