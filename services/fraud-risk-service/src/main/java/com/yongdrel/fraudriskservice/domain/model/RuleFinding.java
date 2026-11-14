package com.yongdrel.fraudriskservice.domain.model;

/** One rule that triggered during a check, with the weight it contributed to the score. */
public record RuleFinding(RuleType ruleType, int weight, String detail) {
}
