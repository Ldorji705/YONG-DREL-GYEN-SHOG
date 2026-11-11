package com.yongdrel.claim.domain.port;

import com.yongdrel.claim.domain.model.PrizeClaim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrizeClaimRepository {

    PrizeClaim save(PrizeClaim claim);

    Optional<PrizeClaim> findById(UUID claimId);

    Optional<PrizeClaim> findByWinnerId(UUID winnerId);

    Optional<PrizeClaim> findByUserIdAndIdempotencyKey(
            String userId,
            String idempotencyKey
    );

    List<PrizeClaim> findAllByUserId(String userId);

    List<PrizeClaim> findAll();

    boolean existsByWinnerId(UUID winnerId);
}
