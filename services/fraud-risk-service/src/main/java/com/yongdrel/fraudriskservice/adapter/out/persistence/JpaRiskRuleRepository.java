package com.yongdrel.fraudriskservice.adapter.out.persistence;

import com.yongdrel.fraudriskservice.application.port.out.RiskRuleRepositoryPort;
import com.yongdrel.fraudriskservice.domain.model.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaRiskRuleRepository implements RiskRuleRepositoryPort {
    private final EntityManager entityManager;
    public JpaRiskRuleRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    @Transactional(readOnly = true)
    public List<RiskRule> findGlobal() {
        return entityManager.createQuery("select r from RiskRuleEntity r where r.tenantId is null order by r.ruleType",
                RiskRuleEntity.class).getResultStream().map(RiskRuleEntity::toDomain).toList();
    }
    @Transactional(readOnly = true)
    public List<RiskRule> findByTenant(Long tenantId) {
        return entityManager.createQuery("select r from RiskRuleEntity r where r.tenantId = :tenantId order by r.ruleType",
                RiskRuleEntity.class).setParameter("tenantId", tenantId).getResultStream()
                .map(RiskRuleEntity::toDomain).toList();
    }
    @Transactional(readOnly = true)
    public boolean exists(Long tenantId, RuleType ruleType) {
        return entityManager.createQuery("""
                select count(r) from RiskRuleEntity r
                where ((:tenantId is null and r.tenantId is null) or r.tenantId = :tenantId)
                and r.ruleType = :ruleType
                """, Long.class).setParameter("tenantId", tenantId).setParameter("ruleType", ruleType)
                .getSingleResult() > 0;
    }
    public RiskRule save(RiskRule rule) {
        RiskRuleEntity entity = entityManager.merge(RiskRuleEntity.from(rule));
        entityManager.flush();
        return entity.toDomain();
    }
    @Transactional(readOnly = true)
    public Optional<RiskRule> findById(Long id) {
        return Optional.ofNullable(entityManager.find(RiskRuleEntity.class, id)).map(RiskRuleEntity::toDomain);
    }
    public void deleteById(Long id) {
        RiskRuleEntity entity = entityManager.find(RiskRuleEntity.class, id);
        if (entity != null) entityManager.remove(entity);
    }
}
