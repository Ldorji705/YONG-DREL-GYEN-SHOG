package com.yongdrel.fraudriskservice.domain.service;

import com.yongdrel.fraudriskservice.domain.model.Decision;
import com.yongdrel.fraudriskservice.domain.model.RiskAssessment;
import com.yongdrel.fraudriskservice.domain.model.RiskLevel;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleFinding;
import com.yongdrel.fraudriskservice.domain.model.RuleType;
import com.yongdrel.fraudriskservice.domain.model.TransactionDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.yongdrel.fraudriskservice.TestData.NOW;
import static com.yongdrel.fraudriskservice.TestData.defaultRules;
import static com.yongdrel.fraudriskservice.TestData.rule;
import static com.yongdrel.fraudriskservice.TestData.tx;
import static org.assertj.core.api.Assertions.assertThat;

class RiskEvaluatorTest {

    private final RiskEvaluator evaluator = new RiskEvaluator(ScoringPolicy.defaults());
    private FakeHistory history;

    @BeforeEach
    void setUp() {
        history = new FakeHistory();
    }

    @Test
    @DisplayName("clean submission scores 0 and is CLEAR")
    void cleanSubmission() {
        RiskAssessment a = evaluator.evaluate(tx(), defaultRules(), history, NOW);

        assertThat(a.score()).isZero();
        assertThat(a.level()).isEqualTo(RiskLevel.NONE);
        assertThat(a.decision()).isEqualTo(Decision.CLEAR);
        assertThat(a.findings()).isEmpty();
    }

    @Test
    @DisplayName("duplicate bank reference on another order raises a HIGH flag")
    void duplicateReference() {
        history.referenceCount = 1;

        RiskAssessment a = evaluator.evaluate(tx(), defaultRules(), history, NOW);

        assertThat(a.findings()).extracting(RuleFinding::ruleType)
                .containsExactly(RuleType.DUPLICATE_PAYMENT_REFERENCE);
        assertThat(a.score()).isEqualTo(70);
        assertThat(a.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(a.decision()).isEqualTo(Decision.REVIEW_REQUIRED);
        assertThat(a.findings().get(0).detail()).contains("1 other order");
    }

    @Test
    @DisplayName("duplicate screenshot hash is detected")
    void duplicateProof() {
        history.proofCount = 2;

        RiskAssessment a = evaluator.evaluate(tx(), defaultRules(), history, NOW);

        assertThat(a.findings()).extracting(RuleFinding::ruleType).containsExactly(RuleType.DUPLICATE_PROOF_IMAGE);
    }

    @Test
    @DisplayName("several findings add up but the score is capped at 100")
    void scoreIsCapped() {
        history.referenceCount = 1;
        history.proofCount = 1;
        history.confirmedFraud = 1;

        RiskAssessment a = evaluator.evaluate(tx(), defaultRules(), history, NOW);

        assertThat(a.findings()).hasSize(3);
        assertThat(a.score()).isEqualTo(100);
    }

    @ParameterizedTest(name = "{0} earlier submissions with threshold 5 -> triggered={1}")
    @CsvSource({"0,false", "4,false", "5,true", "6,true"})
    @DisplayName("velocity rule boundary values")
    void velocityBoundary(long earlier, boolean triggered) {
        history.buyerChecks = earlier;

        RiskAssessment a = evaluator.evaluate(tx(), List.of(rule(RuleType.BUYER_VELOCITY, 5, 10, 25)), history, NOW);

        assertThat(!a.findings().isEmpty()).isEqualTo(triggered);
        assertThat(history.lastVelocitySince).isEqualTo(NOW.minus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("a single LOW finding below the flag threshold stays CLEAR")
    void lowRiskIsNotFlagged() {
        history.buyerChecks = 5;

        RiskAssessment a = evaluator.evaluate(tx(), defaultRules(), history, NOW);

        assertThat(a.score()).isEqualTo(25);
        assertThat(a.level()).isEqualTo(RiskLevel.LOW);
        assertThat(a.decision()).isEqualTo(Decision.CLEAR);
    }

    @ParameterizedTest(name = "amount {0} vs expected 100 with tolerance {1}% -> triggered={2}")
    @CsvSource({
            "100.00, 0, false",
            "99.99, 0, true",
            "110.00, 10, false",
            "110.01, 10, true",
            "50.00, 49, true"})
    @DisplayName("amount mismatch boundary values")
    void amountMismatch(String amount, int tolerance, boolean triggered) {
        TransactionDetails t = new TransactionDetails(10L, 5L, 1000L, 100L, "TXN1", null,
                new BigDecimal(amount), new BigDecimal("100.00"));

        RiskAssessment a = evaluator.evaluate(t, List.of(rule(RuleType.AMOUNT_MISMATCH, tolerance, 0, 40)), history, NOW);

        assertThat(!a.findings().isEmpty()).isEqualTo(triggered);
    }

    @Test
    @DisplayName("rules whose evidence is missing are skipped without querying history")
    void missingEvidenceSkipsRules() {
        TransactionDetails t = new TransactionDetails(10L, 5L, 1000L, 100L, null, null,
                new BigDecimal("100.00"), null);
        history.referenceCount = 5;
        history.proofCount = 5;

        RiskAssessment a = evaluator.evaluate(t, List.of(
                rule(RuleType.DUPLICATE_PAYMENT_REFERENCE, 1, 0, 70),
                rule(RuleType.DUPLICATE_PROOF_IMAGE, 1, 0, 70),
                rule(RuleType.AMOUNT_MISMATCH, 0, 0, 40)), history, NOW);

        assertThat(a.findings()).isEmpty();
        assertThat(history.calls).isZero();
    }

    @Test
    @DisplayName("disabled rules are ignored")
    void disabledRuleIgnored() {
        history.referenceCount = 3;
        RiskRule disabled = new RiskRule(null, null, RuleType.DUPLICATE_PAYMENT_REFERENCE, false, 1, 0, 70,
                null, NOW, null);

        RiskAssessment a = evaluator.evaluate(tx(), List.of(disabled), history, NOW);

        assertThat(a.findings()).isEmpty();
        assertThat(history.calls).isZero();
    }

    @Test
    @DisplayName("window 0 means all history (since epoch)")
    void zeroWindowMeansAllHistory() {
        history.referenceCount = 0;

        evaluator.evaluate(tx(), List.of(rule(RuleType.DUPLICATE_PAYMENT_REFERENCE, 1, 0, 70)), history, NOW);

        assertThat(history.lastReferenceSince).isEqualTo(Instant.EPOCH);
    }

    @Test
    @DisplayName("count rule with threshold 2 ignores a single duplicate")
    void configurableThreshold() {
        history.referenceCount = 1;

        RiskAssessment a = evaluator.evaluate(tx(),
                List.of(rule(RuleType.DUPLICATE_PAYMENT_REFERENCE, 2, 0, 70)), history, NOW);

        assertThat(a.findings()).isEmpty();
    }
}
