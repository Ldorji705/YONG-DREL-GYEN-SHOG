package com.yongdrel.payment_service.application.port;

import java.util.UUID;

public record EventOwnershipVerificationRequest(
        UUID eventId,
        UUID organizerId,
        UUID tenantId
) {
    public EventOwnershipVerificationRequest(UUID eventId, UUID organizerId) {
        this(eventId, organizerId, null);
    }
}
