package com.yongdrel.lotterydraw.adapter.in.web;

import com.yongdrel.lotterydraw.application.service.LotteryDrawApplicationService;
import com.yongdrel.lotterydraw.adapter.out.persistence.DrawEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/draws")
public class DrawController {

    private final LotteryDrawApplicationService service;

    public DrawController(LotteryDrawApplicationService service) {
        this.service = service;
    }

    public record DrawRequest(
            @NotNull UUID eventId,
            @Min(1) int winnerCount
    ) {}

    public record WinnerResponse(
            UUID ticketId,
            int selectionOrder
    ) {}

    public record DrawResponse(
            UUID id,
            UUID eventId,
            String status,
            String executedBy,
            Instant executedAt,
            int eligibleTicketCount,
            int winnerCount,
            List<WinnerResponse> winners
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DrawResponse executeDraw(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DrawRequest request) {

        DrawEntity draw = service.executeDraw(
                request.eventId(),
                request.winnerCount(),
                jwt.getSubject(),
                jwt.getTokenValue()
        );

        return toResponse(draw);
    }

    @GetMapping("/{drawId}")
    public DrawResponse getDraw(
            @PathVariable UUID drawId) {

        return toResponse(service.getDraw(drawId));
    }

    private DrawResponse toResponse(DrawEntity draw) {

        List<WinnerResponse> winners = draw.getWinners()
                .stream()
                .map(winner -> new WinnerResponse(
                        winner.getTicketId(),
                        winner.getSelectionOrder()
                ))
                .toList();

        return new DrawResponse(
                draw.getId(),
                draw.getEventId(),
                draw.getStatus(),
                draw.getExecutedBy(),
                draw.getExecutedAt(),
                draw.getEligibleTicketCount(),
                draw.getWinnerCount(),
                winners
        );
    }
}
