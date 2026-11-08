package com.yongdrel.winner.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record Winner(
        UUID id,
        String drawId,
        UUID eventId,
        String ticketId,
        String userId,
        UUID prizeId,
        LocalDateTime verifiedAt
) {
}
