package com.yongdrel.fraudriskservice;

import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.Decision;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskAssessment;
import com.yongdrel.fraudriskservice.domain.model.RiskCheck;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;
import com.yongdrel.fraudriskservice.domain.model.RiskLevel;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.Role;
import com.yongdrel.fraudriskservice.domain.model.RuleFinding;
import com.yongdrel.fraudriskservice.domain.model.RuleType;
import com.yongdrel.fraudriskservice.domain.model.TransactionDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shared fixtures. Tenant 10 = "our" organizer, tenant 20 = someone else. */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    public static final Long TENANT = 10L;
    public static final Long OTHER_TENANT = 20L;
    public static final String HASH = "a".repeat(64);

    public static final Actor ADMIN = new Actor(1L, Role.ADMIN, null);
    public static final Actor SERVICE = new Actor(2L, Role.SERVICE, null);
    public static final Actor ORGANIZER = new Actor(3L, Role.ORGANIZER, TENANT);
    public static final Actor OTHER_ORGANIZER = new Actor(4L, Role.ORGANIZER, OTHER_TENANT);
    public static final Actor ORGANIZER_WITHOUT_TENANT = new Actor(5L, Role.ORGANIZER, null);
    public static final Actor STUDENT = new Actor(100L, Role.STUDENT, null);

    private TestData() {
    }

    public static TransactionDetails tx() {
        return new TransactionDetails(TENANT, 5L, 1000L, 100L, "TXN-1234", HASH,
                new BigDecimal("100.00"), new BigDecimal("100.00"));
    }

    public static RiskRule rule(RuleType type, int threshold, int windowMinutes, int weight) {
        return new RiskRule(null, null, type, true, threshold, windowMinutes, weight, null, NOW, null);
    }

    public static RiskRule tenantRule(Long id, Long tenantId, RuleType type) {
        return new RiskRule(id, tenantId, type, true, 1, 0, 50, "tenant override", NOW, 0L);
    }

    public static List<RiskRule> defaultRules() {
        return List.of(
                rule(RuleType.DUPLICATE_PAYMENT_REFERENCE, 1, 0, 70),
                rule(RuleType.DUPLICATE_PROOF_IMAGE, 1, 0, 70),
                rule(RuleType.BUYER_VELOCITY, 5, 10, 25),
                rule(RuleType.AMOUNT_MISMATCH, 0, 0, 40),
                rule(RuleType.PRIOR_CONFIRMED_FRAUD, 1, 0, 50));
    }

    public static RiskCheck flaggedCheck(Long id) {
        RiskAssessment a = new RiskAssessment(70, RiskLevel.HIGH, Decision.REVIEW_REQUIRED,
                List.of(new RuleFinding(RuleType.DUPLICATE_PAYMENT_REFERENCE, 70, "dup")));
        return new RiskCheck(id, "k".repeat(64), tx(), a, null, NOW);
    }

    public static RiskFlag openFlag(Long id, Long tenantId) {
        return new RiskFlag(id, 50L, tenantId, 5L, 1000L, 100L, 70, RiskLevel.HIGH,
                List.of(RuleType.DUPLICATE_PAYMENT_REFERENCE), FlagStatus.OPEN, null, null, NOW, null, 0L);
    }
}
