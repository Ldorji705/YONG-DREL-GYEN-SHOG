package com.yongdrel.fraudriskservice.adapter.out.persistence;

import com.yongdrel.fraudriskservice.application.port.out.RiskCheckRepositoryPort;
import com.yongdrel.fraudriskservice.domain.model.RiskCheck;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaRiskCheckRepository implements RiskCheckRepositoryPort {
    private final EntityManager entityManager;
    public JpaRiskCheckRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    @Transactional(readOnly = true)
    public Optional<RiskCheck> findByIdempotencyKey(String key) {
        return entityManager.createQuery("select c from RiskCheckEntity c where c.idempotencyKey = :key",
                        RiskCheckEntity.class).setParameter("key", key).getResultStream()
                .findFirst().map(RiskCheckEntity::toDomain);
    }
    public RiskCheck save(RiskCheck check) {
        RiskCheckEntity entity = entityManager.merge(RiskCheckEntity.from(check));
        entityManager.flush();
        return entity.toDomain();
    }
    @Transactional(readOnly = true)
    public Optional<RiskCheck> findById(Long id) {
        return Optional.ofNullable(entityManager.find(RiskCheckEntity.class, id)).map(RiskCheckEntity::toDomain);
    }
    @Transactional(readOnly = true)
    public long countOtherOrdersWithReference(Long tenantId, String reference, Long excludeOrderId, Instant since) {
        return entityManager.createQuery("""
                select count(c) from RiskCheckEntity c where c.tenantId = :tenantId
                and c.paymentReference = :reference and c.orderId <> :excludeOrderId and c.createdAt >= :since
                """, Long.class).setParameter("tenantId", tenantId).setParameter("reference", reference)
                .setParameter("excludeOrderId", excludeOrderId).setParameter("since", since).getSingleResult();
    }
    @Transactional(readOnly = true)
    public long countOtherOrdersWithProofHash(Long tenantId, String hash, Long excludeOrderId, Instant since) {
        return entityManager.createQuery("""
                select count(c) from RiskCheckEntity c where c.tenantId = :tenantId
                and c.proofImageHash = :hash and c.orderId <> :excludeOrderId and c.createdAt >= :since
                """, Long.class).setParameter("tenantId", tenantId).setParameter("hash", hash)
                .setParameter("excludeOrderId", excludeOrderId).setParameter("since", since).getSingleResult();
    }
    @Transactional(readOnly = true)
    public long countBuyerChecksSince(Long tenantId, Long buyerId, Instant since) {
        return entityManager.createQuery("""
                select count(c) from RiskCheckEntity c where c.tenantId = :tenantId
                and c.buyerId = :buyerId and c.createdAt >= :since
                """, Long.class).setParameter("tenantId", tenantId).setParameter("buyerId", buyerId)
                .setParameter("since", since).getSingleResult();
    }
}
