
package com.yongdrel.ticket_issuance_service.application.service;

import com.yongdrel.ticket_issuance_service.adapter.out.persistence.*;
import com.yongdrel.ticket_issuance_service.domain.model.TicketStatus;
import com.yongdrel.ticket_issuance_service.dto.*;
import com.yongdrel.ticket_issuance_service.messaging.event.TicketIssuedEvent;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventEntity;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;

@Service
public class TicketIssuanceService {

    private final SpringDataTicketRepository repository;
    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public TicketIssuanceService(
            SpringDataTicketRepository repository,
            OutboxEventRepository outboxRepository,
            JsonMapper jsonMapper) {

        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public List<TicketResponse> issueTickets(IssueTicketRequest request) {

        List<TicketEntity> existing =
                repository.findByOrderId(request.orderId());

        if (!existing.isEmpty()) {
            if (existing.size() != request.quantity()
                    || existing.stream().anyMatch(ticket ->
                        !ticket.getEventId().equals(request.eventId())
                        || !ticket.getUserId().equals(request.userId()))) {

                throw new IllegalStateException(
                        "Order already has tickets with different details"
                );
            }

            return existing.stream()
                    .map(TicketResponse::from)
                    .toList();
        }

        List<TicketEntity> tickets = new ArrayList<>();

        Instant issuedAt = Instant.now();

        for (int i = 1; i <= request.quantity(); i++) {

            TicketEntity ticket = new TicketEntity();

            ticket.setId(UUID.randomUUID());

            ticket.setTicketNumber(
                    "YG-" + UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .toUpperCase()
            );

            ticket.setOrderId(request.orderId());
            ticket.setEventId(request.eventId());
            ticket.setUserId(request.userId());
            ticket.setTicketIndex(i);
            ticket.setStatus(TicketStatus.ISSUED);
            ticket.setIssuedAt(issuedAt);

            tickets.add(ticket);
        }

        List<TicketEntity> savedTickets =
                repository.saveAllAndFlush(tickets);

        TicketIssuedEvent event = new TicketIssuedEvent(
                UUID.randomUUID(),
                request.orderId(),
                request.userId(),
                request.eventId(),
                savedTickets.stream()
                        .map(TicketEntity::getId)
                        .toList(),
                issuedAt
        );

        String payload = jsonMapper.writeValueAsString(event);

        OutboxEventEntity outboxEvent =
                new OutboxEventEntity(
                        "TICKET_ISSUED",
                        payload
                );

        outboxRepository.save(outboxEvent);

        return savedTickets.stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getUserTickets(Long userId) {

        return repository.findByUserId(userId)
                .stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TicketResponse> getTicket(UUID ticketId) {

        return repository.findById(ticketId)
                .map(TicketResponse::from);
    }
}
