
package com.yongdrel.claim.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateClaimRequest(

        @NotNull(message = "Winner ID is required")
        UUID winnerId,

        @NotNull(message = "Prize ID is required")
        UUID prizeId

) {
}
