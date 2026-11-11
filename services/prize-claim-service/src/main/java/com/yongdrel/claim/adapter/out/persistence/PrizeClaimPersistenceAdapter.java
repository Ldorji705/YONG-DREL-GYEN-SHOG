package com.yongdrel.claim.adapter.out.persistence;

import com.yongdrel.claim.domain.model.PrizeClaim;
import com.yongdrel.claim.domain.port.PrizeClaimRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PrizeClaimPersistenceAdapter implements PrizeClaimRepository {

    private final PrizeClaimJpaRepository jpaRepository;

    public PrizeClaimPersistenceAdapter(
            PrizeClaimJpaRepository jpaRepository
    ) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PrizeClaim save(PrizeClaim claim) {
        PrizeClaimEntity entity = PrizeClaimEntity.fromDomain(claim);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<PrizeClaim> findById(UUID claimId) {
        return jpaRepository.findById(claimId)
                .map(PrizeClaimEntity::toDomain);
    }

    @Override
    public Optional<PrizeClaim> findByWinnerId(UUID winnerId) {
        return jpaRepository.findByWinnerId(winnerId)
                .map(PrizeClaimEntity::toDomain);
    }

    @Override
    public Optional<PrizeClaim> findByUserIdAndIdempotencyKey(
            String userId,
            String idempotencyKey
    ) {
        return jpaRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(PrizeClaimEntity::toDomain);
    }

    @Override
    public List<PrizeClaim> findAllByUserId(String userId) {
        return jpaRepository
                .findAllByUserIdOrderBySubmittedAtDesc(userId)
                .stream()
                .map(PrizeClaimEntity::toDomain)
                .toList();
    }

    @Override
    public List<PrizeClaim> findAll() {
        return jpaRepository.findAll()
                .stream()
                .map(PrizeClaimEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByWinnerId(UUID winnerId) {
        return jpaRepository.existsByWinnerId(winnerId);
    }
}