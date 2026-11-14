package com.yongdrel.fraudriskservice.adapter.in.web.dto;

import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleType;

import java.time.Instant;

public record RiskRuleResponse(
        Long id,
        Long tenantId,
        String scope,
        RuleType ruleType,
        boolean enabled,
        int threshold,
        int windowMinutes,
        int weight,
        String description,
        Instant updatedAt
) {

    public static RiskRuleResponse from(RiskRule r) {
        return new RiskRuleResponse(r.getId(), r.getTenantId(), r.isGlobal() ? "GLOBAL" : "TENANT",
                r.getRuleType(), r.isEnabled(), r.getThreshold(), r.getWindowMinutes(), r.getWeight(),
                r.getDescription(), r.getUpdatedAt());
    }
}
