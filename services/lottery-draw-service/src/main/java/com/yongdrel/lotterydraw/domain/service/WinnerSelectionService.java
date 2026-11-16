package com.yongdrel.lotterydraw.domain.service;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class WinnerSelectionService {

    public static final String ALGORITHM_VERSION = "SHA256_COUNTER_FISHER_YATES_V1";

    public List<UUID> selectWinners(
            List<UUID> eligibleTicketIds,
            int winnerCount) {
        byte[] seed = new byte[32];
        new java.security.SecureRandom().nextBytes(seed);
        return selectWinners(eligibleTicketIds, winnerCount, HexFormat.of().formatHex(seed));
    }

    public List<UUID> selectWinners(
            List<UUID> eligibleTicketIds,
            int winnerCount,
            String seedHex) {

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
        if (seedHex == null || !seedHex.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Seed must be 32 bytes encoded as hexadecimal");
        }

        List<UUID> shuffled = new ArrayList<>(eligibleTicketIds);
        byte[] seed = HexFormat.of().parseHex(seedHex);
        long counter = 0;

        // Partial Fisher-Yates shuffle:
        // Every eligible ticket has an equal selection probability.
        for (int i = 0; i < winnerCount; i++) {
            int selectedIndex = i + nextInt(seed, counter++, shuffled.size() - i);

            UUID temp = shuffled.get(i);
            shuffled.set(i, shuffled.get(selectedIndex));
            shuffled.set(selectedIndex, temp);
        }

        return List.copyOf(shuffled.subList(0, winnerCount));
    }

    private static int nextInt(byte[] seed, long counter, int bound) {
        long limit = (1L << 32) - ((1L << 32) % bound);
        while (true) {
            byte[] digest = digest(seed, counter++);
            long value = ByteBuffer.wrap(digest).getInt() & 0xffffffffL;
            if (value < limit) return (int) (value % bound);
        }
    }

    private static byte[] digest(byte[] seed, long counter) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(seed);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
