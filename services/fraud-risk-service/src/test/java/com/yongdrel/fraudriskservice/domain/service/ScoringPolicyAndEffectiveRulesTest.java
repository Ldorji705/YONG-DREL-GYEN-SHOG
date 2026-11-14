package com.yongdrel.fraudriskservice.domain.service;

import com.yongdrel.fraudriskservice.domain.model.RiskLevel;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.yongdrel.fraudriskservice.TestData.TENANT;
import static com.yongdrel.fraudriskservice.TestData.defaultRules;
import static com.yongdrel.fraudriskservice.TestData.tenantRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringPolicyAndEffectiveRulesTest {

    @ParameterizedTest(name = "score {0} -> {1}")
    @CsvSource({"0,NONE", "1,LOW", "29,LOW", "30,MEDIUM", "69,MEDIUM", "70,HIGH", "100,HIGH"})
    void levelBoundaries(int score, RiskLevel expected) {
        assertThat(ScoringPolicy.defaults().levelFor(score)).isEqualTo(expected);
    }

    @Test
    void reviewThreshold() {
        ScoringPolicy policy = ScoringPolicy.defaults();
        assertThat(policy.requiresReview(29)).isFalse();
        assertThat(policy.requiresReview(30)).isTrue();
    }

    @ParameterizedTest(name = "flag={0} medium={1} high={2} is rejected")
    @CsvSource({"30,0,70", "30,80,70", "30,30,101", "0,30,70", "101,30,70"})
    void invalidPolicies(int flag, int medium, int high) {
        assertThatThrownBy(() -> new ScoringPolicy(flag, medium, high))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tenantRuleOverridesDefaultOfSameType() {
        RiskRule override = tenantRule(99L, TENANT, RuleType.BUYER_VELOCITY);

        List<RiskRule> effective = EffectiveRules.merge(defaultRules(), List.of(override));

        assertThat(effective).hasSize(RuleType.values().length);
        assertThat(effective).filteredOn(r -> r.getRuleType() == RuleType.BUYER_VELOCITY)
                .singleElement()
                .extracting(RiskRule::getTenantId).isEqualTo(TENANT);
    }
}
