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

    @GetMapping("/{id}/ownership")
    public boolean ownership(@PathVariable UUID id,@RequestParam UUID organizerId,@RequestParam UUID tenantId,@RequestHeader(value="X-Internal-API-Key",required=false)String key){authorize(key);return service.ownsTenant(id,tenantId)&&service.findById(id).createdBy.equals(organizerId.toString());}
    @GetMapping("/{id}/capacity")
    public Capacity capacity(@PathVariable UUID id,@RequestHeader(value="X-Internal-API-Key",required=false)String key){authorize(key);EventEntity e=service.capacity(id);return new Capacity(e.id,e.tenantId,e.maxTickets,e.reservedTickets);}
    @PostMapping("/{id}/close")
    public void close(@PathVariable UUID id,@RequestHeader(value="X-Internal-API-Key",required=false)String key){authorize(key);service.close(id);}
    @GetMapping("/closing")
    public java.util.List<Closing> closing(@RequestParam java.time.Instant before,@RequestParam(defaultValue="100")int limit,@RequestHeader(value="X-Internal-API-Key",required=false)String key){authorize(key);if(limit<1||limit>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid limit");    return service.closing(java.time.LocalDateTime.ofInstant(before,java.time.ZoneOffset.UTC)).stream().limit(limit).map(e->new Closing(e.id,e.tenantId,e.endDate.toInstant(java.time.ZoneOffset.UTC))).toList();}
    public record Capacity(UUID eventId,UUID tenantId,int capacity,int reserved){}
    public record Closing(UUID id,UUID tenantId,java.time.Instant expiresAt){}
    private void authorize(String providedKey){byte[] actual=providedKey==null?new byte[0]:providedKey.getBytes(StandardCharsets.UTF_8);if(!MessageDigest.isEqual(expectedKey,actual))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid internal API key");}
}
