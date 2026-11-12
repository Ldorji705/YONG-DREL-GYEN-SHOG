package com.yongdrel.lotterydraw.application.port;

import java.util.UUID;

public interface EventEligibilityPort {

    void verifyEligibleForDraw(
            UUID eventId,
            String authenticatedUserId,
            String accessToken
    );
}
