
package com.yongdrel.winner.adapter.in.web;

import com.yongdrel.winner.adapter.in.web.dto.CreateWinnerRequest;
import com.yongdrel.winner.adapter.in.web.dto.WinnerResponse;
import com.yongdrel.winner.application.service.WinnerApplicationService;
import com.yongdrel.winner.domain.model.Winner;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/winners")
public class WinnerController {

    private final WinnerApplicationService winnerApplicationService;

    public WinnerController(
            WinnerApplicationService winnerApplicationService) {
        this.winnerApplicationService = winnerApplicationService;
    }

    // Create a winner (Admin only)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public WinnerResponse createWinner(
            @Valid @RequestBody CreateWinnerRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Winner winner = winnerApplicationService.createWinner(
                request.drawId(),
                request.eventId(),
                request.ticketId(),
                request.userId(),
                request.prizeId(),
                jwt.getTokenValue()
        );

        return WinnerResponse.from(winner);
    }

    // Retrieve winners belonging to the authenticated user
    @GetMapping("/me")
    public List<WinnerResponse> getMyWinners(
            @AuthenticationPrincipal Jwt jwt) {

        return winnerApplicationService
                .getMyWinners(jwt.getSubject())
                .stream()
                .map(WinnerResponse::from)
                .toList();
    }

    // Retrieve winners for a particular event (Admin only)
    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WinnerResponse> getWinnersByEvent(
            @PathVariable UUID eventId) {

        return winnerApplicationService
                .getWinnersByEvent(eventId)
                .stream()
                .map(WinnerResponse::from)
                .toList();
    }

    // Retrieve a specific winner (Owner only)
    @GetMapping("/{id}")
    public WinnerResponse getWinnerById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        Winner winner = winnerApplicationService.getWinnerById(id);

        if (!winner.userId().equals(jwt.getSubject())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to view this winner"
            );
        }

        return WinnerResponse.from(winner);
    }

    // Verify a winner (Admin only)
    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public WinnerResponse verifyWinner(@PathVariable UUID id) {

        Winner winner = winnerApplicationService.verifyWinner(id);

        return WinnerResponse.from(winner);
    }
}
