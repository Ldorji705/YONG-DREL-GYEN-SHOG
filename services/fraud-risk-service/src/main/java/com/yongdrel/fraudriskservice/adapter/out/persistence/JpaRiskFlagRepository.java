package com.yongdrel.fraudriskservice.adapter.out.persistence;

import com.yongdrel.fraudriskservice.application.model.*;
import com.yongdrel.fraudriskservice.application.port.out.RiskFlagRepositoryPort;
import com.yongdrel.fraudriskservice.domain.model.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
public class JpaRiskFlagRepository implements RiskFlagRepositoryPort {
    private final EntityManager entityManager;
    public JpaRiskFlagRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public RiskFlag save(RiskFlag flag) {
        RiskFlagEntity entity = entityManager.merge(RiskFlagEntity.from(flag));
        entityManager.flush();
        return entity.toDomain();
    }
    @Transactional(readOnly = true)
    public PageResult<RiskFlag> search(FlagSearchCriteria c, int page, int size) {
        String where = " where (:tenantId is null or f.tenantId = :tenantId)"
                + " and (:eventId is null or f.eventId = :eventId)"
                + " and (:orderId is null or f.orderId = :orderId)"
                + " and (:buyerId is null or f.buyerId = :buyerId)"
                + " and (:status is null or f.status = :status)"
                + " and (:riskLevel is null or f.riskLevel = :riskLevel)";
        TypedQuery<RiskFlagEntity> query = entityManager.createQuery(
                "select f from RiskFlagEntity f" + where + " order by f.createdAt desc, f.id desc",
                RiskFlagEntity.class);
        bind(query, c);
        query.setFirstResult(page * size).setMaxResults(size);
        List<RiskFlag> content = query.getResultList().stream().map(RiskFlagEntity::toDomain).toList();
        Long total = entityManager.createQuery("select count(f) from RiskFlagEntity f" + where, Long.class)
                .setParameter("tenantId", c.tenantId()).setParameter("eventId", c.eventId())
                .setParameter("orderId", c.orderId()).setParameter("buyerId", c.buyerId())
                .setParameter("status", c.status()).setParameter("riskLevel", c.riskLevel()).getSingleResult();
        return new PageResult<>(content, page, size, total);
    }
    @Transactional(readOnly = true)
    public Optional<RiskFlag> findById(Long id) {
        return Optional.ofNullable(entityManager.find(RiskFlagEntity.class, id)).map(RiskFlagEntity::toDomain);
    }
    @Transactional(readOnly = true)
    public long countByBuyerAndStatus(Long tenantId, Long buyerId, FlagStatus status) {
        return entityManager.createQuery("""
                select count(f) from RiskFlagEntity f where f.tenantId = :tenantId
                and f.buyerId = :buyerId and f.status = :status
                """, Long.class).setParameter("tenantId", tenantId).setParameter("buyerId", buyerId)
                .setParameter("status", status).getSingleResult();
    }
    private static void bind(TypedQuery<?> query, FlagSearchCriteria c) {
        query.setParameter("tenantId", c.tenantId()).setParameter("eventId", c.eventId())
                .setParameter("orderId", c.orderId()).setParameter("buyerId", c.buyerId())
                .setParameter("status", c.status()).setParameter("riskLevel", c.riskLevel());
    }
}
