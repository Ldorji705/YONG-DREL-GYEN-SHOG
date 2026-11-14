
package com.yongdrel.winner.application.service;

import com.yongdrel.winner.adapter.out.client.ReferenceValidationClient;
import com.yongdrel.winner.adapter.out.persistence.WinnerEntity;
import com.yongdrel.winner.adapter.out.persistence.WinnerRepository;
import com.yongdrel.winner.domain.model.Winner;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WinnerApplicationService {

    private final WinnerRepository winnerRepository;
    private final ReferenceValidationClient referenceValidationClient;

    public WinnerApplicationService(
            WinnerRepository winnerRepository,
            ReferenceValidationClient referenceValidationClient) {
        this.winnerRepository = winnerRepository;
        this.referenceValidationClient = referenceValidationClient;
    }

    public Winner createWinner(
            String drawId,
            UUID eventId,
            String ticketId,
            String userId,
            UUID prizeId,
            String accessToken
    ) {
        referenceValidationClient.validateEvent(eventId, accessToken);
        referenceValidationClient.validatePrize(prizeId, accessToken);

        if (winnerRepository.existsByDrawIdAndTicketId(drawId, ticketId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This ticket is already registered as a winner for this draw"
            );
        }

        Winner winner = new Winner(
                null,
                drawId,
                eventId,
                ticketId,
                userId,
                prizeId,
                null
        );

        WinnerEntity savedWinner = winnerRepository.save(
                new WinnerEntity(winner)
        );

        return savedWinner.toDomain();
    }

    @Transactional(readOnly = true)
    public Winner getWinnerById(UUID id) {
        return winnerRepository.findById(id)
                .map(WinnerEntity::toDomain)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Winner not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<Winner> getWinnersByEvent(UUID eventId) {
        return winnerRepository.findByEventId(eventId)
                .stream()
                .map(WinnerEntity::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Winner> getMyWinners(String userId) {
        return winnerRepository.findByUserId(userId)
                .stream()
                .map(WinnerEntity::toDomain)
                .toList();
    }

    public Winner verifyWinner(UUID id) {
        WinnerEntity winner = winnerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Winner not found"
                ));

        if (winner.getVerifiedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Winner has already been verified"
            );
        }

        winner.setVerifiedAt(LocalDateTime.now());

        return winnerRepository.save(winner).toDomain();
    }
}
