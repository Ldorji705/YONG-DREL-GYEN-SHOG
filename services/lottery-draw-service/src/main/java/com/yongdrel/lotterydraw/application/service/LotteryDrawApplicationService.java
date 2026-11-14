package com.yongdrel.lotterydraw.application.service;

import com.yongdrel.lotterydraw.adapter.out.persistence.DrawEntity;
import com.yongdrel.lotterydraw.adapter.out.persistence.DrawRepository;
import com.yongdrel.lotterydraw.application.port.EligibleTicketPort;
import com.yongdrel.lotterydraw.application.port.EventEligibilityPort;
import com.yongdrel.lotterydraw.domain.service.WinnerSelectionService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class LotteryDrawApplicationService {

    private final DrawRepository drawRepository;
    private final EventEligibilityPort eventEligibilityPort;
    private final EligibleTicketPort eligibleTicketPort;
    private final WinnerSelectionService winnerSelectionService;

    public LotteryDrawApplicationService(
            DrawRepository drawRepository,
            EventEligibilityPort eventEligibilityPort,
            EligibleTicketPort eligibleTicketPort) {

        this.drawRepository = drawRepository;
        this.eventEligibilityPort = eventEligibilityPort;
        this.eligibleTicketPort = eligibleTicketPort;
        this.winnerSelectionService = new WinnerSelectionService();
    }

    @Transactional
    public DrawEntity executeDraw(
            UUID eventId,
            int winnerCount,
            String authenticatedUserId,
            String accessToken) {

        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (authenticatedUserId == null ||
                authenticatedUserId.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated user ID is required"
            );
        }

        if (winnerCount <= 0) {
            throw new IllegalArgumentException(
                    "Winner count must be greater than zero"
            );
        }

        // Fast rejection for an existing draw.
        if (drawRepository.existsByEventId(eventId)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "A draw already exists for this event"
            );
        }

        // Must verify COMPLETED status, end time and ownership.
        eventEligibilityPort.verifyEligibleForDraw(
                eventId,
                authenticatedUserId,
                accessToken
        );

        // Must come from a trusted, finalized ticket source.
        List<UUID> eligibleTickets =
                eligibleTicketPort.getEligibleTicketIds(eventId);

        List<UUID> selectedWinners =
                winnerSelectionService.selectWinners(
                        eligibleTickets,
                        winnerCount
                );

        DrawEntity draw = new DrawEntity(
                eventId,
                authenticatedUserId,
                eligibleTickets.size(),
                winnerCount
        );

        for (int i = 0; i < selectedWinners.size(); i++) {
            draw.addWinner(selectedWinners.get(i), i + 1);
        }

        try {
            // Flush now to detect database constraint violations.
            return drawRepository.saveAndFlush(draw);

        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "A draw already exists or a database constraint was violated",
                    exception
            );
        }
    }

    @Transactional(readOnly = true)
    public DrawEntity getDraw(UUID drawId) {
        return drawRepository.findById(drawId)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Draw not found"
                ));
    }
}
