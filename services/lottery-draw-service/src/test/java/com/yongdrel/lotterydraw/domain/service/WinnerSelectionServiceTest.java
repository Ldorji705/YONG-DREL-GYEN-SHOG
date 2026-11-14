package com.yongdrel.lotterydraw.domain.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WinnerSelectionServiceTest {

    private final WinnerSelectionService service =
            new WinnerSelectionService();

    private final List<UUID> tickets = List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
    );

    @Test
    void shouldSelectRequestedNumberOfWinners() {
        List<UUID> winners = service.selectWinners(tickets, 2);

        assertEquals(2, winners.size());
        assertTrue(tickets.containsAll(winners));
    }

    @Test
    void shouldNotSelectDuplicateWinners() {
        List<UUID> winners = service.selectWinners(tickets, 5);

        assertEquals(5, new HashSet<>(winners).size());
    }

    @Test
    void shouldRejectZeroWinners() {
        assertThrows(IllegalArgumentException.class,
                () -> service.selectWinners(tickets, 0));
    }

    @Test
    void shouldRejectExcessiveWinnerCount() {
        assertThrows(IllegalArgumentException.class,
                () -> service.selectWinners(tickets, 6));
    }

    @Test
    void shouldRejectEmptyTicketList() {
        assertThrows(IllegalArgumentException.class,
                () -> service.selectWinners(List.of(), 1));
    }

    @Test
    void shouldRejectDuplicateTicketIds() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.selectWinners(List.of(id, id), 1));
    }

    @Test
    void shouldRejectNullTicketList() {
        assertThrows(IllegalArgumentException.class,
                () -> service.selectWinners(null, 1));
    }

    @Test
    void shouldRejectNullTicketId() {
        List<UUID> invalidTickets = new java.util.ArrayList<>(tickets);
        invalidTickets.add(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.selectWinners(invalidTickets, 1));
    }
}
