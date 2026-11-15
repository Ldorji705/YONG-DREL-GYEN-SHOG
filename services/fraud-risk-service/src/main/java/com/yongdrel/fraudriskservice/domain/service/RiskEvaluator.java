package com.yongdrel.fraudriskservice.domain.service;

import com.yongdrel.fraudriskservice.domain.model.Decision;
import com.yongdrel.fraudriskservice.domain.model.RiskAssessment;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleFinding;
import com.yongdrel.fraudriskservice.domain.model.TransactionDetails;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies the enabled rules to a submission. Each triggered rule adds its weight;
 * the score is capped at 100 and mapped to a level and decision by the ScoringPolicy.
 */
public class RiskEvaluator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ScoringPolicy policy;

    public RiskEvaluator(ScoringPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public RiskAssessment evaluate(TransactionDetails tx, Collection<RiskRule> rules,
                                   RiskHistory history, Instant now) {
        List<RuleFinding> findings = rules.stream()
                .filter(RiskRule::isEnabled)
                .sorted(Comparator.comparing(RiskRule::getRuleType))
                .map(rule -> apply(rule, tx, history, now))
                .flatMap(Optional::stream)
                .toList();

        int score = Math.min(100, findings.stream().mapToInt(RuleFinding::weight).sum());
        Decision decision = policy.requiresReview(score) ? Decision.REVIEW_REQUIRED : Decision.CLEAR;
        return new RiskAssessment(score, policy.levelFor(score), decision, findings);
    }

    private Optional<RuleFinding> apply(RiskRule rule, TransactionDetails tx, RiskHistory history, Instant now) {
        Instant since = rule.windowStart(now);
        return switch (rule.getRuleType()) {
            case DUPLICATE_PAYMENT_REFERENCE -> {
                if (tx.paymentReference() == null) {
                    yield Optional.empty();
                }
                long n = history.countOtherOrdersWithReference(tx.tenantId(), tx.paymentReference(), tx.orderId(), since);
                yield countFinding(rule, n, "Payment reference already used on %d other order(s)");
            }
            case DUPLICATE_PROOF_IMAGE -> {
                if (tx.proofImageHash() == null) {
                    yield Optional.empty();
                }
                long n = history.countOtherOrdersWithProofHash(tx.tenantId(), tx.proofImageHash(), tx.orderId(), since);
                yield countFinding(rule, n, "Payment screenshot already used on %d other order(s)");
            }
            case BUYER_VELOCITY -> {
                long n = history.countBuyerChecksSince(tx.tenantId(), tx.buyerId(), since);
                yield countFinding(rule, n, "Buyer made %d earlier submission(s) within the window");
            }
            case PRIOR_CONFIRMED_FRAUD -> {
                long n = history.countConfirmedFraudFlags(tx.tenantId(), tx.buyerId());
                yield countFinding(rule, n, "Buyer has %d flag(s) confirmed as fraud");
            }
            case AMOUNT_MISMATCH -> amountFinding(rule, tx);
        };
    }

    private Optional<RuleFinding> countFinding(RiskRule rule, long count, String template) {
        if (count > 0 && count >= rule.getThreshold()) {
            return Optional.of(new RuleFinding(rule.getRuleType(), rule.getWeight(), template.formatted(count)));
        }
        return Optional.empty();
    }

    private Optional<RuleFinding> amountFinding(RiskRule rule, TransactionDetails tx) {
        BigDecimal expected = tx.expectedAmount();
        if (expected == null || expected.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal diffPercent = tx.amount().subtract(expected).abs()
                .multiply(HUNDRED)
                .divide(expected, 2, RoundingMode.HALF_UP);
        if (diffPercent.compareTo(BigDecimal.valueOf(rule.getThreshold())) > 0) {
            return Optional.of(new RuleFinding(rule.getRuleType(), rule.getWeight(),
                    "Declared amount %s differs from expected %s by %s%%"
                            .formatted(tx.amount().toPlainString(), expected.toPlainString(), diffPercent.toPlainString())));
        }
        return Optional.empty();
    }
}
