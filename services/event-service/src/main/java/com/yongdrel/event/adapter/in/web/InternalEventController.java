package com.yongdrel.event.adapter.in.web;

import com.yongdrel.event.adapter.out.persistence.EventEntity;
import com.yongdrel.event.application.service.EventApplicationService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

    private final EventApplicationService service;
    private final byte[] expectedKey;

    public InternalEventController(
            EventApplicationService service,
            @Value("${app.internal.api-key}") String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Internal Event API key must be configured"
            );
        }

        this.service = service;
        this.expectedKey =
                apiKey.getBytes(StandardCharsets.UTF_8);
    }

    public record EventInfo(
            UUID id,
            String status,
            LocalDateTime endDate
    ) {}

    @GetMapping("/{id}")
    public EventInfo getEvent(
            @PathVariable UUID id,
            @RequestHeader(
                    value = "X-Internal-API-Key",
                    required = false
            ) String providedKey) {

        byte[] actualKey = providedKey == null
                ? new byte[0]
                : providedKey.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedKey, actualKey)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid internal API key"
            );
        }

        EventEntity event = service.findById(id);

        return new EventInfo(
                event.id,
                event.status.name(),
                event.endDate
        );
    }
}
