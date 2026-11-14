package com.yongdrel.event.adapter.in.web;

import com.yongdrel.event.adapter.in.web.dto.EventRequest;
import com.yongdrel.event.adapter.out.persistence.EventEntity;
import com.yongdrel.event.application.service.EventApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventApplicationService service;

    public EventController(EventApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventEntity create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody EventRequest request) {
        return service.create(request, jwt.getSubject());
    }

    @GetMapping
    public List<EventEntity> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public EventEntity get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public EventEntity update(
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody EventRequest request) {
        return service.update(id, request, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt) {
        service.delete(id, jwt.getSubject());
    }
}
