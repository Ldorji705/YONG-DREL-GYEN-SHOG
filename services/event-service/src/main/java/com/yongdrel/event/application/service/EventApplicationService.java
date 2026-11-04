package com.yongdrel.event.application.service;

import com.yongdrel.event.adapter.in.web.dto.EventRequest;
import com.yongdrel.event.adapter.out.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class EventApplicationService {

    private final EventRepository repository;

    public EventApplicationService(EventRepository repository) {
        this.repository = repository;
    }

    public EventEntity create(EventRequest request, String userId) {
        validateDates(request);

        EventEntity event = new EventEntity();
        event.createdBy = userId;
        apply(event, request);

        return repository.save(event);
    }

    public List<EventEntity> findAll() {
        return repository.findAll();
    }

    public EventEntity findById(UUID id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Event not found"));
    }

    public EventEntity update(UUID id, EventRequest request, String userId) {
        validateDates(request);

        EventEntity event = findById(id);
        requireOwner(event, userId);
        apply(event, request);

        return repository.save(event);
    }

    public void delete(UUID id, String userId) {
        EventEntity event = findById(id);
        requireOwner(event, userId);
        repository.delete(event);
    }

    private void requireOwner(EventEntity event, String userId) {
        if (!event.createdBy.equals(userId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Not event owner");
        }
    }

    private void validateDates(EventRequest request) {
        if (request.startDate() != null &&
            request.endDate() != null &&
            !request.endDate().isAfter(request.startDate())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "End date must be after start date");
        }
    }

    private void apply(EventEntity event, EventRequest request) {
        event.title = request.title().trim();
        event.description = request.description();
        event.status = request.status();
        event.startDate = request.startDate();
        event.endDate = request.endDate();
    }
}
