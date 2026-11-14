package com.yongdrel.fraudriskservice.adapter.out.persistence;

import com.yongdrel.fraudriskservice.domain.model.*;
import jakarta.persistence.*;

@Embeddable
public class RiskFindingEmbeddable {
    @Enumerated(EnumType.STRING) @Column(name = "rule_type", nullable = false, length = 50)
    private RuleType ruleType;
    @Column(nullable = false) private int weight;
    @Column(length = 255) private String detail;

    protected RiskFindingEmbeddable() {
    }

    static RiskFindingEmbeddable from(RuleFinding value) {
        RiskFindingEmbeddable finding = new RiskFindingEmbeddable();
        finding.ruleType = value.ruleType();
        finding.weight = value.weight();
        finding.detail = value.detail();
        return finding;
    }

    RuleFinding toDomain() {
        return new RuleFinding(ruleType, weight, detail);
    }
}
