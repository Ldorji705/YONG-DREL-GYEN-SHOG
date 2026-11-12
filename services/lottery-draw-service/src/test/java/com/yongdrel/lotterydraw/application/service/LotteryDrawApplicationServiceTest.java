package com.yongdrel.lotterydraw.application.service;

import com.yongdrel.lotterydraw.adapter.out.persistence.DrawEntity;
import com.yongdrel.lotterydraw.adapter.out.persistence.DrawRepository;
import com.yongdrel.lotterydraw.application.port.EligibleTicketPort;
import com.yongdrel.lotterydraw.application.port.EventEligibilityPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LotteryDrawApplicationServiceTest {

    private DrawRepository repository;
    private EventEligibilityPort eventPort;
    private EligibleTicketPort ticketPort;
    private LotteryDrawApplicationService service;

    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(DrawRepository.class);
        eventPort = mock(EventEligibilityPort.class);
        ticketPort = mock(EligibleTicketPort.class);

        service = new LotteryDrawApplicationService(
                repository,
                eventPort,
                ticketPort
        );
    }

    @Test
    void shouldExecuteDrawSuccessfully() {
        when(repository.existsByEventId(eventId))
                .thenReturn(false);

        when(ticketPort.getEligibleTicketIds(eventId))
                .thenReturn(List.of(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID()
                ));

        when(repository.saveAndFlush(any(DrawEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DrawEntity result = service.executeDraw(
                eventId, 2, "123"
        , "test-access-token");

        assertEquals(eventId, result.getEventId());
        assertEquals(3, result.getEligibleTicketCount());
        assertEquals(2, result.getWinnerCount());
        assertEquals(2, result.getWinners().size());

        verify(eventPort).verifyEligibleForDraw(eventId, "123", "test-access-token");
        verify(repository).saveAndFlush(any(DrawEntity.class));
    }

    @Test
    void shouldRejectDuplicateDraw() {
        when(repository.existsByEventId(eventId))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.executeDraw(eventId, 1, "123", "test-access-token")
        );

        assertEquals(409, exception.getStatusCode().value());

        verifyNoInteractions(eventPort, ticketPort);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectInvalidWinnerCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.executeDraw(eventId, 0, "123", "test-access-token")
        );

        verifyNoInteractions(eventPort, ticketPort);
    }

    @Test
    void shouldRejectUnauthorizedEvent() {
        doThrow(new SecurityException("Not the event owner"))
                .when(eventPort)
                .verifyEligibleForDraw(eventId, "456", "test-access-token");

        assertThrows(
                SecurityException.class,
                () -> service.executeDraw(eventId, 1, "456", "test-access-token")
        );

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectInsufficientTickets() {
        when(ticketPort.getEligibleTicketIds(eventId))
                .thenReturn(List.of(UUID.randomUUID()));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.executeDraw(eventId, 2, "123", "test-access-token")
        );

        verify(repository, never()).saveAndFlush(any());
    }
}
