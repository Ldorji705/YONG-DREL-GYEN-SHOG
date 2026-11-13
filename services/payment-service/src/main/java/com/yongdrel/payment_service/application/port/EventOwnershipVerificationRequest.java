package com.yongdrel.payment_service.application.port;

import java.util.UUID;

public record EventOwnershipVerificationRequest(
        UUID eventId,
        UUID organizerId
) {}
