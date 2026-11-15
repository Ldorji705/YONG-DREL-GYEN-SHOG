package com.yongdrel.fraudriskservice.domain.model;

import java.util.List;

public record RiskAssessment(int score, RiskLevel level, Decision decision, List<RuleFinding> findings) {

    public RiskAssessment {
        findings = List.copyOf(findings);
    }
}
