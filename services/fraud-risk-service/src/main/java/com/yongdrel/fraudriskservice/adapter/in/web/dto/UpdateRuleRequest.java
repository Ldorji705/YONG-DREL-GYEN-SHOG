package com.yongdrel.fraudriskservice.adapter.in.web.dto;

import com.yongdrel.fraudriskservice.application.port.in.RuleSettings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** tenantId and ruleType are immutable; create a new rule instead. */
public record UpdateRuleRequest(
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
