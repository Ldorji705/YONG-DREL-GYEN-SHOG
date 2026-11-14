package com.yongdrel.fraudriskservice.application.service;

import com.yongdrel.fraudriskservice.application.exception.DependencyUnavailableException;
import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.application.exception.ReferenceValidationException;
import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.model.OrderSnapshot;
import com.yongdrel.fraudriskservice.application.port.in.AssessTransactionCommand;
import com.yongdrel.fraudriskservice.application.port.in.AssessmentResult;
import com.yongdrel.fraudriskservice.application.port.out.FlagEventPublisherPort;
import com.yongdrel.fraudriskservice.application.port.out.OrderReferencePort;
import com.yongdrel.fraudriskservice.application.port.out.RiskCheckRepositoryPort;
import com.yongdrel.fraudriskservice.application.port.out.RiskFlagRepositoryPort;
import com.yongdrel.fraudriskservice.application.port.out.RiskRuleRepositoryPort;
import com.yongdrel.fraudriskservice.domain.model.Decision;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskCheck;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleType;
import com.yongdrel.fraudriskservice.domain.service.RiskEvaluator;
import com.yongdrel.fraudriskservice.domain.service.ScoringPolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.yongdrel.fraudriskservice.TestData.ADMIN;
import static com.yongdrel.fraudriskservice.TestData.HASH;
import static com.yongdrel.fraudriskservice.TestData.NOW;
import static com.yongdrel.fraudriskservice.TestData.ORGANIZER;
import static com.yongdrel.fraudriskservice.TestData.ORGANIZER_WITHOUT_TENANT;
import static com.yongdrel.fraudriskservice.TestData.OTHER_ORGANIZER;
import static com.yongdrel.fraudriskservice.TestData.OTHER_TENANT;
import static com.yongdrel.fraudriskservice.TestData.SERVICE;
import static com.yongdrel.fraudriskservice.TestData.STUDENT;
import static com.yongdrel.fraudriskservice.TestData.TENANT;
import static com.yongdrel.fraudriskservice.TestData.defaultRules;
import static com.yongdrel.fraudriskservice.TestData.flaggedCheck;
import static com.yongdrel.fraudriskservice.TestData.tenantRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    @Mock RiskCheckRepositoryPort checks;
    @Mock RiskFlagRepositoryPort flags;
    @Mock RiskRuleRepositoryPort rules;
    @Mock OrderReferencePort orders;
    @Mock FlagEventPublisherPort publisher;

    private RiskAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new RiskAssessmentService(checks, flags, rules, orders, publisher,
                new RiskEvaluator(ScoringPolicy.defaults()), Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(orders.isEnabled()).thenReturn(false);
        lenient().when(rules.findGlobal()).thenReturn(defaultRules());
        lenient().when(rules.findByTenant(any())).thenReturn(List.of());
        lenient().when(checks.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        lenient().when(checks.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 50L));
        lenient().when(flags.save(any())).thenAnswer(inv -> flagWithId(inv.getArgument(0), 7L));
    }

    private static AssessTransactionCommand command(Long tenantId, Long buyerId) {
        return new AssessTransactionCommand(tenantId, 5L, 1000L, buyerId, "txn-1234", HASH,
                new BigDecimal("100.00"), new BigDecimal("100.00"));
    }

    private static RiskCheck withId(RiskCheck c, Long id) {
        return new RiskCheck(c.id() == null ? id : c.id(), c.idempotencyKey(), c.transaction(), c.assessment(),
                c.flagId(), c.createdAt());
    }

    private static RiskFlag flagWithId(RiskFlag f, Long id) {
        return new RiskFlag(id, f.getCheckId(), f.getTenantId(), f.getEventId(), f.getOrderId(), f.getBuyerId(),
                f.getScore(), f.getRiskLevel(), f.getReasons(), f.getStatus(), f.getReviewerId(), f.getReviewNote(),
                f.getCreatedAt(), f.getReviewedAt(), 0L);
    }

    @Nested
    class Assessing {

        @Test
        void cleanSubmissionIsStoredWithoutFlag() {
            AssessmentResult result = service.assess(command(TENANT, 100L), SERVICE);

            assertThat(result.created()).isTrue();
            assertThat(result.check().assessment().decision()).isEqualTo(Decision.CLEAR);
            assertThat(result.check().flagId()).isNull();
            assertThat(result.check().transaction().paymentReference()).isEqualTo("TXN1234");
            verify(checks, times(1)).save(any());
            verify(flags, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        void duplicateReferenceRaisesFlagLinksItAndPublishes() {
            when(checks.countOtherOrdersWithReference(eq(TENANT), eq("TXN1234"), eq(1000L), any())).thenReturn(1L);

            AssessmentResult result = service.assess(command(TENANT, 100L), ORGANIZER);

            assertThat(result.check().assessment().decision()).isEqualTo(Decision.REVIEW_REQUIRED);
            assertThat(result.check().flagId()).isEqualTo(7L);

            ArgumentCaptor<RiskFlag> flag = ArgumentCaptor.forClass(RiskFlag.class);
            verify(flags).save(flag.capture());
            assertThat(flag.getValue().getStatus()).isEqualTo(FlagStatus.OPEN);
            assertThat(flag.getValue().getCheckId()).isEqualTo(50L);
            assertThat(flag.getValue().getReasons()).containsExactly(RuleType.DUPLICATE_PAYMENT_REFERENCE);
            verify(checks, times(2)).save(any());
            verify(publisher).flagRaised(any(RiskFlag.class));
        }

        @Test
        void priorConfirmedFraudIsLookedUpFromFlags() {
            when(flags.countByBuyerAndStatus(TENANT, 100L, FlagStatus.CONFIRMED_FRAUD)).thenReturn(1L);

            AssessmentResult result = service.assess(command(TENANT, 100L), ADMIN);

            assertThat(result.check().assessment().findings())
                    .extracting(f -> f.ruleType()).containsExactly(RuleType.PRIOR_CONFIRMED_FRAUD);
        }

        @Test
        void historyCountsAreDelegatedToPorts() {
            when(checks.countOtherOrdersWithProofHash(eq(TENANT), eq(HASH), eq(1000L), any())).thenReturn(1L);
            when(checks.countBuyerChecksSince(eq(TENANT), eq(100L), any())).thenReturn(9L);

            AssessmentResult result = service.assess(command(TENANT, 100L), ADMIN);

            assertThat(result.check().assessment().findings()).extracting(f -> f.ruleType())
                    .containsExactly(RuleType.DUPLICATE_PROOF_IMAGE, RuleType.BUYER_VELOCITY);
        }

        @Test
        void tenantOverrideReplacesDefault() {
            RiskRule disabledDup = new RiskRule(9L, TENANT, RuleType.DUPLICATE_PAYMENT_REFERENCE, false, 1, 0, 70,
                    null, NOW, 0L);
            when(rules.findByTenant(TENANT)).thenReturn(List.of(disabledDup));
            lenient().when(checks.countOtherOrdersWithReference(any(), any(), anyLong(), any())).thenReturn(3L);

            AssessmentResult result = service.assess(command(TENANT, 100L), ADMIN);

            assertThat(result.check().assessment().decision()).isEqualTo(Decision.CLEAR);
        }

        @Test
        void identicalResubmissionReturnsExistingCheck() {
            RiskCheck existing = flaggedCheck(42L);
            when(checks.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existing));

            AssessmentResult result = service.assess(command(TENANT, 100L), SERVICE);

            assertThat(result.created()).isFalse();
            assertThat(result.check()).isSameAs(existing);
            verify(checks, never()).save(any());
            verifyNoInteractions(publisher);
        }
    }

    @Nested
    class Authorization {

        @Test
        void studentMaySubmitForOwnOrder() {
            assertThat(service.assess(command(TENANT, STUDENT.userId()), STUDENT).created()).isTrue();
        }

        @Test
        void studentCannotSubmitForSomeoneElse() {
            assertThatThrownBy(() -> service.assess(command(TENANT, 999L), STUDENT))
                    .isInstanceOf(ForbiddenOperationException.class);
            verifyNoInteractions(checks);
        }

        @Test
        void organizerCannotSubmitForAnotherTenant() {
            assertThatThrownBy(() -> service.assess(command(OTHER_TENANT, 100L), ORGANIZER))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void organizerWithoutTenantClaimIsRejected() {
            assertThatThrownBy(() -> service.assess(command(TENANT, 100L), ORGANIZER_WITHOUT_TENANT))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        void getCheckHidesOtherTenantsAndForbidsStudents() {
            when(checks.findById(42L)).thenReturn(Optional.of(flaggedCheck(42L)));

            assertThat(service.getCheck(42L, ORGANIZER).id()).isEqualTo(42L);
            assertThat(service.getCheck(42L, SERVICE).id()).isEqualTo(42L);
            assertThatThrownBy(() -> service.getCheck(42L, OTHER_ORGANIZER))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.getCheck(42L, STUDENT))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        void getUnknownCheckIs404() {
            when(checks.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getCheck(1L, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class CrossServiceReference {

        @BeforeEach
        void enable() {
            when(orders.isEnabled()).thenReturn(true);
        }

        @Test
        void matchingOrderIsAccepted() {
            when(orders.findOrder(1000L)).thenReturn(Optional.of(new OrderSnapshot(1000L, TENANT, 5L, 100L, "AWAITING_VERIFICATION")));

            assertThat(service.assess(command(TENANT, 100L), SERVICE).created()).isTrue();
        }

        @Test
        void unknownFieldsFromOrderServiceAreNotCompared() {
            when(orders.findOrder(1000L)).thenReturn(Optional.of(new OrderSnapshot(1000L, null, null, null, null)));

            assertThat(service.assess(command(TENANT, 100L), SERVICE).created()).isTrue();
        }

        @Test
        void missingOrderIsRejected() {
            when(orders.findOrder(1000L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assess(command(TENANT, 100L), SERVICE))
                    .isInstanceOf(ReferenceValidationException.class)
                    .hasMessageContaining("does not exist");
            verify(checks, never()).save(any());
        }

        @Test
        void orderOfAnotherTenantIsRejected() {
            when(orders.findOrder(1000L)).thenReturn(Optional.of(new OrderSnapshot(1000L, OTHER_TENANT, 5L, 100L, null)));

            assertThatThrownBy(() -> service.assess(command(TENANT, 100L), SERVICE))
                    .isInstanceOf(ReferenceValidationException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        void orderOfAnotherBuyerIsRejected() {
            when(orders.findOrder(1000L)).thenReturn(Optional.of(new OrderSnapshot(1000L, TENANT, 5L, 555L, null)));

            assertThatThrownBy(() -> service.assess(command(TENANT, 100L), SERVICE))
                    .isInstanceOf(ReferenceValidationException.class)
                    .hasMessageContaining("buyerId");
        }

        @Test
        void orderServiceOutageIsPropagated() {
            when(orders.findOrder(1000L)).thenThrow(new DependencyUnavailableException("down"));

            assertThatThrownBy(() -> service.assess(command(TENANT, 100L), SERVICE))
                    .isInstanceOf(DependencyUnavailableException.class);
            verify(checks, never()).save(any());
        }
    }
}
