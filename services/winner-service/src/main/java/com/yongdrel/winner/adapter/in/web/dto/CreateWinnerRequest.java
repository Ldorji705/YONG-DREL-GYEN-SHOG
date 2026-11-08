
package com.yongdrel.winner.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWinnerRequest(

        @NotBlank(message = "Draw ID is required")
        String drawId,

        @NotNull(message = "Event ID is required")
        UUID eventId,

        @NotBlank(message = "Ticket ID is required")
        String ticketId,

        @NotBlank(message = "User ID is required")
        String userId,

        @NotNull(message = "Prize ID is required")
        UUID prizeId

) {}
