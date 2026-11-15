package com.yongdrel.fraudriskservice.application.port.out;

import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleType;
import java.util.List;
import java.util.Optional;

public interface RiskRuleRepositoryPort {
    List<RiskRule> findGlobal();
    List<RiskRule> findByTenant(Long tenantId);
    boolean exists(Long tenantId, RuleType ruleType);
    RiskRule save(RiskRule rule);
    Optional<RiskRule> findById(Long id);
    void deleteById(Long id);
}
