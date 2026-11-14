package com.yongdrel.claim.adapter.out.client;

import java.time.LocalDateTime;
import java.util.UUID;

public record WinnerReferenceResponse(
        UUID id,
        String drawId,
        UUID eventId,
        String ticketId,
        String userId,
        UUID prizeId,
        LocalDateTime verifiedAt
) {
}