package com.yongdrel.fraudriskservice.domain.service;

import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Merges platform defaults with a tenant's overrides: a tenant rule replaces the default of the same type. */
public final class EffectiveRules {

    private EffectiveRules() {
    }

    public static List<RiskRule> merge(Collection<RiskRule> globalRules, Collection<RiskRule> tenantRules) {
        Map<RuleType, RiskRule> byType = new EnumMap<>(RuleType.class);
        globalRules.forEach(rule -> byType.put(rule.getRuleType(), rule));
        tenantRules.forEach(rule -> byType.put(rule.getRuleType(), rule));
        return List.copyOf(byType.values());
    }
}
