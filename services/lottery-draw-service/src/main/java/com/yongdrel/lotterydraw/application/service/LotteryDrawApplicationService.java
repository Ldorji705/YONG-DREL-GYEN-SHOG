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
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

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
        List<UUID> eligibleTickets = eligibleTicketPort.getEligibleTicketIds(eventId)
                .stream().sorted().toList();
        if (eligibleTickets.isEmpty()) {
            throw new IllegalArgumentException("No eligible tickets are available");
        }
        if (winnerCount > eligibleTickets.size()) {
            throw new IllegalArgumentException("Winner count exceeds eligible ticket count");
        }
        String snapshot = eligibleTickets.stream().map(UUID::toString)
                .collect(Collectors.joining("\n"));
        String snapshotHash = sha256(snapshot);
        byte[] seedBytes = new byte[32];
        new SecureRandom().nextBytes(seedBytes);
        String seed = HexFormat.of().formatHex(seedBytes);
        String commitment = sha256(seed);

        DrawEntity draw = new DrawEntity(
                eventId,
                authenticatedUserId,
                eligibleTickets.size(),
                winnerCount,
                commitment,
                null,
                snapshotHash,
                snapshot,
                WinnerSelectionService.ALGORITHM_VERSION
        );

        try {
            drawRepository.saveAndFlush(draw);
            List<UUID> selectedWinners = winnerSelectionService.selectWinners(
                    eligibleTickets, winnerCount, seed);
            draw.complete(seed, selectedWinners);
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
