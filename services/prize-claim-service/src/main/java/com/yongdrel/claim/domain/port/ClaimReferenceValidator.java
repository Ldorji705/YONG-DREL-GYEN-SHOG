package com.yongdrel.claim.domain.port;

import java.util.UUID;

public interface ClaimReferenceValidator {

    void validate(
            UUID winnerId,
            UUID prizeId,
            String userId,
            String accessToken
    );
}