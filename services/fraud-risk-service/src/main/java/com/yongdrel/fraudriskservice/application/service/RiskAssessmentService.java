package com.yongdrel.fraudriskservice.application.service;

import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.application.exception.ReferenceValidationException;
import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.model.OrderSnapshot;
import com.yongdrel.fraudriskservice.application.port.in.AssessTransactionCommand;
import com.yongdrel.fraudriskservice.application.port.in.AssessTransactionUseCase;
import com.yongdrel.fraudriskservice.application.port.in.AssessmentResult;
import com.yongdrel.fraudriskservice.application.port.out.FlagEventPublisherPort;
import com.yongdrel.fraudriskservice.application.port.out.OrderReferencePort;
import com.yongdrel.fraudriskservice.application.port.out.RiskCheckRepositoryPort;
import com.yongdrel.fraudriskservice.application.port.out.RiskFlagRepositoryPort;
import com.yongdrel.fraudriskservice.application.port.out.RiskRuleRepositoryPort;
import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.Decision;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskAssessment;
import com.yongdrel.fraudriskservice.domain.model.RiskCheck;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.Role;
import com.yongdrel.fraudriskservice.domain.model.TransactionDetails;
import com.yongdrel.fraudriskservice.domain.service.EffectiveRules;
import com.yongdrel.fraudriskservice.domain.service.RiskEvaluator;
import com.yongdrel.fraudriskservice.domain.service.RiskHistory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class RiskAssessmentService implements AssessTransactionUseCase {

    private final RiskCheckRepositoryPort checks;
    private final RiskFlagRepositoryPort flags;
    private final RiskRuleRepositoryPort rules;
    private final OrderReferencePort orders;
    private final FlagEventPublisherPort publisher;
    private final RiskEvaluator evaluator;
    private final Clock clock;

    public RiskAssessmentService(RiskCheckRepositoryPort checks, RiskFlagRepositoryPort flags,
                                 RiskRuleRepositoryPort rules, OrderReferencePort orders,
                                 FlagEventPublisherPort publisher, RiskEvaluator evaluator, Clock clock) {
        this.checks = checks;
        this.flags = flags;
        this.rules = rules;
        this.orders = orders;
        this.publisher = publisher;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AssessmentResult assess(AssessTransactionCommand cmd, Actor actor) {
        AccessPolicy.requireCanAssess(actor, cmd.tenantId(), cmd.buyerId());

        TransactionDetails tx = new TransactionDetails(cmd.tenantId(), cmd.eventId(), cmd.orderId(),
                cmd.buyerId(), cmd.paymentReference(), cmd.proofImageHash(), cmd.amount(), cmd.expectedAmount());
        String key = IdempotencyKey.of(tx);

        var existing = checks.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return new AssessmentResult(existing.get(), false);
        }

        verifyOrderReference(tx);

        Instant now = clock.instant();
        List<RiskRule> effective = EffectiveRules.merge(rules.findGlobal(), rules.findByTenant(tx.tenantId()));
        RiskAssessment assessment = evaluator.evaluate(tx, effective, history(), now);

        RiskCheck saved = checks.save(new RiskCheck(null, key, tx, assessment, null, now));

        if (assessment.decision() == Decision.REVIEW_REQUIRED) {
            RiskFlag flag = flags.save(RiskFlag.raise(saved, now));
            saved = checks.save(saved.withFlagId(flag.getId()));
            publisher.flagRaised(flag);
        }
        return new AssessmentResult(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public RiskCheck getCheck(Long checkId, Actor actor) {
        if (actor.is(Role.STUDENT)) {
            throw new ForbiddenOperationException("Students cannot view risk checks");
        }
        return checks.findById(checkId)
                .filter(check -> AccessPolicy.canViewTenant(actor, check.transaction().tenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Risk check " + checkId + " not found"));
    }

    /** The order must exist in the Order Service and match the tenant, event and buyer we were given. */
    private void verifyOrderReference(TransactionDetails tx) {
        if (!orders.isEnabled()) {
            return;
        }
        OrderSnapshot order = orders.findOrder(tx.orderId())
                .orElseThrow(() -> new ReferenceValidationException("Order " + tx.orderId() + " does not exist"));
        mismatch("tenantId", order.tenantId(), tx.tenantId());
        mismatch("eventId", order.eventId(), tx.eventId());
        mismatch("buyerId", order.buyerId(), tx.buyerId());
    }

    private static void mismatch(String field, Long fromOrder, Long fromRequest) {
        if (fromOrder != null && !Objects.equals(fromOrder, fromRequest)) {
            throw new ReferenceValidationException(
                    "Order " + field + " is " + fromOrder + " but request says " + fromRequest);
        }
    }

    private RiskHistory history() {
        return new RiskHistory() {
            @Override
            public long countOtherOrdersWithReference(Long tenantId, String ref, Long excludeOrderId, Instant since) {
                return checks.countOtherOrdersWithReference(tenantId, ref, excludeOrderId, since);
            }

            @Override
            public long countOtherOrdersWithProofHash(Long tenantId, String hash, Long excludeOrderId, Instant since) {
                return checks.countOtherOrdersWithProofHash(tenantId, hash, excludeOrderId, since);
            }

            @Override
            public long countBuyerChecksSince(Long tenantId, Long buyerId, Instant since) {
                return checks.countBuyerChecksSince(tenantId, buyerId, since);
            }

            @Override
            public long countConfirmedFraudFlags(Long tenantId, Long buyerId) {
                return flags.countByBuyerAndStatus(tenantId, buyerId, FlagStatus.CONFIRMED_FRAUD);
            }
        };
    }
}
