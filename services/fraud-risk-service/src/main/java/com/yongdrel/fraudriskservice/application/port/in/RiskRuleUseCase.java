package com.yongdrel.fraudriskservice.application.port.in;

import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleType;

import java.util.List;

public interface RiskRuleUseCase {

    /** tenantId == null lists the platform defaults. */
    List<RiskRule> list(Long tenantId, Actor actor);

    /** Defaults merged with the tenant's overrides: what is actually applied. */
    List<RiskRule> effective(Long tenantId, Actor actor);

    RiskRule get(Long ruleId, Actor actor);

    RiskRule create(Long tenantId, RuleType ruleType, RuleSettings settings, Actor actor);

    RiskRule update(Long ruleId, RuleSettings settings, Actor actor);

    void delete(Long ruleId, Actor actor);
}
