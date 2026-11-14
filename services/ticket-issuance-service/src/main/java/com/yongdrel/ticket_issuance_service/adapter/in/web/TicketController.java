
package com.yongdrel.ticket_issuance_service.adapter.in.web;

import com.yongdrel.ticket_issuance_service.application.service.TicketIssuanceService;
import com.yongdrel.ticket_issuance_service.dto.TicketResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketIssuanceService service;

    public TicketController(TicketIssuanceService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public List<TicketResponse> myTickets(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = authenticatedUserId(jwt);
        return service.getUserTickets(userId);
    }

    @GetMapping("/{ticketId}")
    public TicketResponse getTicket(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = authenticatedUserId(jwt);

        TicketResponse ticket = service.getTicket(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket not found"
                ));

        if (!ticket.userId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ticket not found"
            );
        }

        return ticket;
    }

    private UUID authenticatedUserId(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user identity"
            );
        }
    }
}
