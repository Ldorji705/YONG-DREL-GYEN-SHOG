
package com.yongdrel.ticket_issuance_service.application.service;

import com.yongdrel.ticket_issuance_service.adapter.out.event.EventServiceAdapter;
import com.yongdrel.ticket_issuance_service.adapter.out.persistence.*;
import com.yongdrel.ticket_issuance_service.domain.model.TicketStatus;
import com.yongdrel.ticket_issuance_service.dto.*;
import com.yongdrel.ticket_issuance_service.messaging.event.TicketIssuedEvent;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventEntity;
import com.yongdrel.ticket_issuance_service.messaging.outbox.OutboxEventRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class TicketIssuanceService {

    private static final ZoneId BHUTAN_ZONE =
            ZoneId.of("Asia/Thimphu");

    private final SpringDataTicketRepository repository;
    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final EventServiceAdapter eventServiceAdapter;

    public TicketIssuanceService(
            SpringDataTicketRepository repository,
            OutboxEventRepository outboxRepository,
            JsonMapper jsonMapper,
            EventServiceAdapter eventServiceAdapter) {

        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.eventServiceAdapter = eventServiceAdapter;
    }

    @Transactional
    public List<TicketResponse> issueTickets(IssueTicketRequest request) {

        // Step 1: Prevent duplicate ticket issuance
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

        // Step 2: Validate the lottery event
        EventServiceAdapter.EventInfo event =
                eventServiceAdapter.getEvent(request.eventId());

        if (!"ACTIVE".equals(event.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tickets can only be issued for ACTIVE events"
            );
        }

        LocalDateTime now = LocalDateTime.now(BHUTAN_ZONE);

        if (!now.isBefore(event.endDate())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket issuance is closed for this event"
            );
        }

        // Step 3: Generate tickets
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

        // Step 4: Save tickets
        List<TicketEntity> savedTickets =
                repository.saveAllAndFlush(tickets);

        // Step 5: Create ticket-issued event
        TicketIssuedEvent eventMessage = new TicketIssuedEvent(
                UUID.randomUUID(),
                request.orderId(),
                request.userId(),
                request.eventId(),
                savedTickets.stream()
                        .map(TicketEntity::getId)
                        .toList(),
                issuedAt
        );

        String payload =
                jsonMapper.writeValueAsString(eventMessage);

        // Step 6: Save event to outbox
        OutboxEventEntity outboxEvent =
                new OutboxEventEntity(
                        "TICKET_ISSUED",
                        payload
                );

        outboxRepository.save(outboxEvent);

        // Step 7: Return issued tickets
        return savedTickets.stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getEligibleTicketIds(UUID eventId) {

        Objects.requireNonNull(
                eventId,
                "Event ID is required"
        );

        return repository.findByEventIdAndStatus(
                eventId,
                TicketStatus.ISSUED
        ).stream()
         .map(TicketEntity::getId)
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
