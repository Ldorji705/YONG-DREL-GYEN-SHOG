
package com.yongdrel.event.application.service;

import com.yongdrel.event.adapter.in.web.dto.EventRequest;
import com.yongdrel.event.adapter.out.persistence.*;
import com.yongdrel.event.domain.model.EventStatus;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class EventApplicationService {

    private final EventRepository repository;

    public EventApplicationService(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EventEntity create(EventRequest request, String userId) {
        return create(request, userId, null);
    }

    @Transactional
    public EventEntity create(EventRequest request, String userId, UUID tenantId) {
        validateDates(request);
        validateCapacity(request);

        EventEntity event = new EventEntity();
        event.createdBy = userId;
        event.tenantId = tenantId;
        apply(event, request);

        return repository.save(event);
    }

    @Transactional(readOnly = true)
    public boolean ownsTenant(UUID id, UUID tenantId) {
        EventEntity event = findById(id);
        return tenantId != null && tenantId.equals(event.tenantId);
    }

    @Transactional(readOnly = true)
    public EventEntity capacity(UUID id) {
        return findById(id);
    }
    @Transactional(readOnly = true)
    public List<EventEntity> closing(java.time.LocalDateTime before) {
        return repository.findTop100ByStatusInAndEndDateBeforeOrderByEndDateAsc(
                List.of(EventStatus.ACTIVE, EventStatus.SCHEDULED), before);
    }

    @Transactional
    public void close(UUID id) {
        EventEntity event = repository.findByIdForUpdate(id).orElseThrow();
        if (event.endDate.isAfter(java.time.LocalDateTime.now(java.time.Clock.systemUTC()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is not closed");
        }
        if (event.status == EventStatus.ACTIVE || event.status == EventStatus.SCHEDULED) {
            event.status = EventStatus.COMPLETED;
        }
    }

    public List<EventEntity> findAll() {
        return repository.findAll();
    }

    public EventEntity findById(UUID id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Event not found"
            )
        );
    }

    @Transactional
    public EventEntity update(
            UUID id,
            EventRequest request,
            String userId) {

        validateDates(request);
        validateCapacity(request);

        EventEntity event = repository.findByIdForUpdate(id)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found"
                    )
                );

        requireOwner(event, userId);

        if (request.maxTickets() < event.reservedTickets) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Capacity cannot be lower than reserved tickets"
            );
        }

        apply(event, request);

        return repository.save(event);
    }

    @Transactional
    public void delete(UUID id, String userId) {

        EventEntity event = repository.findByIdForUpdate(id)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found"
                    )
                );

        requireOwner(event, userId);

        if (event.reservedTickets > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot delete an event with reserved tickets"
            );
        }

        repository.delete(event);
    }

    @Transactional
    public void reserveCapacity(UUID eventId, int quantity) {

        if (quantity < 1 || quantity > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Quantity must be between 1 and 100"
            );
        }

        int updated = repository.reserveCapacity(
                eventId,
                quantity
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Event is inactive or has insufficient capacity"
            );
        }
    }

    private void requireOwner(EventEntity event, String userId) {
        if (!event.createdBy.equals(userId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Not event owner"
            );
        }
    }

    private void validateDates(EventRequest request) {
        if (request.startDate() != null &&
            request.endDate() != null &&
            !request.endDate().isAfter(request.startDate())) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "End date must be after start date"
            );
        }
    }

    private void validateCapacity(EventRequest request) {
        if (request.maxTickets() == null ||
            request.maxTickets() < 1) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Maximum ticket capacity must be at least 1"
            );
        }
    }

    private void apply(EventEntity event, EventRequest request) {

        event.title = request.title().trim();
        event.description = request.description();
        event.status = request.status();
        event.startDate = request.startDate();
        event.endDate = request.endDate();
        event.maxTickets = request.maxTickets();
    }
}
