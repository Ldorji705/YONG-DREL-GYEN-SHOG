package com.yongdrel.claim.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrizeClaimJpaRepository
        extends JpaRepository<PrizeClaimEntity, UUID> {

    List<PrizeClaimEntity> findAllByUserIdOrderBySubmittedAtDesc(
            String userId
    );

    Optional<PrizeClaimEntity> findByWinnerId(UUID winnerId);

    Optional<PrizeClaimEntity> findByUserIdAndIdempotencyKey(
            String userId,
            String idempotencyKey
    );

    boolean existsByWinnerId(UUID winnerId);
}