package com.yongdrel.winner.adapter.in.web.dto;

import com.yongdrel.winner.domain.model.Winner;

import java.time.LocalDateTime;
import java.util.UUID;

public record WinnerResponse(
        UUID id,
        String drawId,
        UUID eventId,
        String ticketId,
        String userId,
        UUID prizeId,
        LocalDateTime verifiedAt
) {
    public static WinnerResponse from(Winner winner) {
        return new WinnerResponse(
                winner.id(),
                winner.drawId(),
                winner.eventId(),
                winner.ticketId(),
                winner.userId(),
                winner.prizeId(),
                winner.verifiedAt()
        );
    }
}
