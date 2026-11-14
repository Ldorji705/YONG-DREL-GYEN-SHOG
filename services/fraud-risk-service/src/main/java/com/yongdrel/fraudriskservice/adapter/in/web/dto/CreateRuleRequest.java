package com.yongdrel.fraudriskservice.adapter.in.web.dto;

import com.yongdrel.fraudriskservice.application.port.in.RuleSettings;
import com.yongdrel.fraudriskservice.domain.model.RuleType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** tenantId omitted = platform default (admin only). */
public record CreateRuleRequest(
        @Positive Long tenantId,
        @NotNull RuleType ruleType,
        @NotNull Boolean enabled,
        @NotNull @Min(0) @Max(1000) Integer threshold,
        @NotNull @Min(0) @Max(525600) Integer windowMinutes,
        @NotNull @Min(1) @Max(100) Integer weight,
        @Size(max = 255) String description
) {

    public RuleSettings settings() {
        return new RuleSettings(enabled, threshold, windowMinutes, weight, description);
    }
}
