package com.yongdrel.ticket_issuance_service.adapter.in.web;

import com.yongdrel.ticket_issuance_service.application.service.TicketIssuanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/tickets")
public class InternalTicketController {

    private final TicketIssuanceService service;
    private final byte[] expectedKey;

    public InternalTicketController(
            TicketIssuanceService service,
            @Value("${app.internal.api-key}") String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Internal API key must be configured"
            );
        }

        this.service = service;
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/eligible")
    public List<UUID> eligibleTickets(
            @RequestParam UUID eventId,
            @RequestHeader(
                    value = "X-Internal-API-Key",
                    required = false
            ) String suppliedKey) {

        if (suppliedKey == null ||
                !MessageDigest.isEqual(
                        expectedKey,
                        suppliedKey.getBytes(StandardCharsets.UTF_8))) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid internal credentials"
            );
        }

        return service.getEligibleTicketIds(eventId);
    }
}
