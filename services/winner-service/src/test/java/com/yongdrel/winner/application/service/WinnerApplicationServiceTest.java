
package com.yongdrel.winner.application.service;

import com.yongdrel.winner.adapter.out.client.ReferenceValidationClient;
import com.yongdrel.winner.adapter.out.persistence.WinnerEntity;
import com.yongdrel.winner.adapter.out.persistence.WinnerRepository;
import com.yongdrel.winner.domain.model.Winner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WinnerApplicationServiceTest {

    @Mock
    private WinnerRepository winnerRepository;

    @Mock
    private ReferenceValidationClient referenceValidationClient;

    @InjectMocks
    private WinnerApplicationService winnerApplicationService;

    private UUID winnerId;
    private UUID eventId;
    private UUID prizeId;

    private String drawId;
    private String ticketId;
    private String userId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        winnerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        prizeId = UUID.randomUUID();

        drawId = "DRAW-001";
        ticketId = "TICKET-001";
        userId = "1001";
        accessToken = "test-access-token";
    }

    @Test
    void createWinnerSuccessfully() {
        when(winnerRepository.existsByDrawIdAndTicketId(drawId, ticketId))
                .thenReturn(false);

        when(winnerRepository.save(any(WinnerEntity.class)))
                .thenAnswer(invocation -> {
                    WinnerEntity entity = invocation.getArgument(0);
                    return entity;
                });

        Winner result = winnerApplicationService.createWinner(
                drawId,
                eventId,
                ticketId,
                userId,
                prizeId,
                accessToken
        );

        assertNotNull(result);
        assertEquals(drawId, result.drawId());
        assertEquals(eventId, result.eventId());
        assertEquals(ticketId, result.ticketId());
        assertEquals(userId, result.userId());
        assertEquals(prizeId, result.prizeId());
        assertNull(result.verifiedAt());

        verify(referenceValidationClient).validateEvent(eventId, accessToken);
        verify(referenceValidationClient).validatePrize(prizeId, accessToken);
        verify(winnerRepository).save(any(WinnerEntity.class));
    }

    @Test
    void createWinnerRejectsDuplicateTicket() {
        when(winnerRepository.existsByDrawIdAndTicketId(drawId, ticketId))
                .thenReturn(true);

        assertThrows(
                ResponseStatusException.class,
                () -> winnerApplicationService.createWinner(
                        drawId,
                        eventId,
                        ticketId,
                        userId,
                        prizeId,
                        accessToken
                )
        );

        verify(referenceValidationClient).validateEvent(eventId, accessToken);
        verify(referenceValidationClient).validatePrize(prizeId, accessToken);
        verify(winnerRepository, never()).save(any(WinnerEntity.class));
    }

    @Test
    void createWinnerDoesNotSaveWhenEventReferenceIsInvalid() {
        doThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Event reference does not exist"
        )).when(referenceValidationClient)
                .validateEvent(eventId, accessToken);

        assertThrows(
                ResponseStatusException.class,
                () -> winnerApplicationService.createWinner(
                        drawId,
                        eventId,
                        ticketId,
                        userId,
                        prizeId,
                        accessToken
                )
        );

        verify(referenceValidationClient).validateEvent(eventId, accessToken);
        verify(referenceValidationClient, never())
                .validatePrize(any(UUID.class), anyString());
        verify(winnerRepository, never()).save(any(WinnerEntity.class));
    }

    @Test
    void createWinnerDoesNotSaveWhenPrizeReferenceIsInvalid() {
        doThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Prize reference does not exist"
        )).when(referenceValidationClient)
                .validatePrize(prizeId, accessToken);

        assertThrows(
                ResponseStatusException.class,
                () -> winnerApplicationService.createWinner(
                        drawId,
                        eventId,
                        ticketId,
                        userId,
                        prizeId,
                        accessToken
                )
        );

        verify(referenceValidationClient).validateEvent(eventId, accessToken);
        verify(referenceValidationClient).validatePrize(prizeId, accessToken);
        verify(winnerRepository, never()).save(any(WinnerEntity.class));
    }

    @Test
    void getWinnerByIdSuccessfully() {
        WinnerEntity entity = createWinnerEntity();

        when(winnerRepository.findById(winnerId))
                .thenReturn(Optional.of(entity));

        Winner result = winnerApplicationService.getWinnerById(winnerId);

        assertEquals(winnerId, result.id());
        assertEquals(drawId, result.drawId());
        verify(winnerRepository).findById(winnerId);
    }

    @Test
    void getWinnerByIdThrowsWhenNotFound() {
        when(winnerRepository.findById(winnerId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> winnerApplicationService.getWinnerById(winnerId)
        );
    }

    @Test
    void getWinnersByEventSuccessfully() {
        WinnerEntity entity = createWinnerEntity();

        when(winnerRepository.findByEventId(eventId))
                .thenReturn(List.of(entity));

        List<Winner> result =
                winnerApplicationService.getWinnersByEvent(eventId);

        assertEquals(1, result.size());
        assertEquals(eventId, result.get(0).eventId());
        verify(winnerRepository).findByEventId(eventId);
    }

    @Test
    void getMyWinnersSuccessfully() {
        WinnerEntity entity = createWinnerEntity();

        when(winnerRepository.findByUserId(userId))
                .thenReturn(List.of(entity));

        List<Winner> result =
                winnerApplicationService.getMyWinners(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).userId());
        verify(winnerRepository).findByUserId(userId);
    }

    @Test
    void verifyWinnerSuccessfully() {
        WinnerEntity entity = createWinnerEntity();

        when(winnerRepository.findById(winnerId))
                .thenReturn(Optional.of(entity));

        when(winnerRepository.save(any(WinnerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Winner result = winnerApplicationService.verifyWinner(winnerId);

        assertNotNull(result.verifiedAt());
        verify(winnerRepository).save(entity);
    }

    @Test
    void verifyWinnerThrowsWhenNotFound() {
        when(winnerRepository.findById(winnerId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> winnerApplicationService.verifyWinner(winnerId)
        );

        verify(winnerRepository, never()).save(any(WinnerEntity.class));
    }

    @Test
    void verifyWinnerThrowsWhenAlreadyVerified() {
        WinnerEntity entity = createWinnerEntity();
        entity.setVerifiedAt(LocalDateTime.now());

        when(winnerRepository.findById(winnerId))
                .thenReturn(Optional.of(entity));

        assertThrows(
                ResponseStatusException.class,
                () -> winnerApplicationService.verifyWinner(winnerId)
        );

        verify(winnerRepository, never()).save(any(WinnerEntity.class));
    }

    private WinnerEntity createWinnerEntity() {
        Winner winner = new Winner(
                winnerId,
                drawId,
                eventId,
                ticketId,
                userId,
                prizeId,
                null
        );

        return new WinnerEntity(winner);
    }
}
