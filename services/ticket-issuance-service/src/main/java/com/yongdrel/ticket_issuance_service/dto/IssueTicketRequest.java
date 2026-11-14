
package com.yongdrel.ticket_issuance_service.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record IssueTicketRequest(
        @NotNull UUID orderId,
        @NotNull UUID eventId,
        @NotNull UUID userId,
        @Min(1) @Max(100) int quantity
) {}
