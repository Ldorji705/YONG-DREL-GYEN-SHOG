package com.yongdrel.lotterydraw.domain.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class WinnerSelectionService {

    private final SecureRandom random;

    public WinnerSelectionService() {
        this(new SecureRandom());
    }

    public WinnerSelectionService(SecureRandom random) {
        this.random = Objects.requireNonNull(random);
    }

    public List<UUID> selectWinners(
            List<UUID> eligibleTicketIds,
            int winnerCount) {

        if (eligibleTicketIds == null || eligibleTicketIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Eligible ticket list cannot be empty"
            );
        }

        if (winnerCount <= 0) {
            throw new IllegalArgumentException(
                    "Winner count must be greater than zero"
            );
        }

        if (eligibleTicketIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Eligible ticket list cannot contain null IDs"
            );
        }

        Set<UUID> uniqueIds = new HashSet<>(eligibleTicketIds);

        if (uniqueIds.size() != eligibleTicketIds.size()) {
            throw new IllegalArgumentException(
                    "Duplicate ticket IDs are not allowed"
            );
        }

        if (winnerCount > eligibleTicketIds.size()) {
            throw new IllegalArgumentException(
                    "Winner count exceeds eligible ticket count"
            );
        }

        List<UUID> shuffled = new ArrayList<>(eligibleTicketIds);

        // Partial Fisher-Yates shuffle:
        // Every eligible ticket has an equal selection probability.
        for (int i = 0; i < winnerCount; i++) {
            int selectedIndex =
                    i + random.nextInt(shuffled.size() - i);

            UUID temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(selectedIndex));
            shuffled.set(selectedIndex, temp);
        }

        return List.copyOf(shuffled.subList(0, winnerCount));
    }
}
