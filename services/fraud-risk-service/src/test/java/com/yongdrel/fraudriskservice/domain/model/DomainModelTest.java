package com.yongdrel.fraudriskservice.domain.model;

import com.yongdrel.fraudriskservice.domain.exception.InvalidFlagTransitionException;
import com.yongdrel.fraudriskservice.domain.exception.InvalidRuleException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;

import static com.yongdrel.fraudriskservice.TestData.NOW;
import static com.yongdrel.fraudriskservice.TestData.TENANT;
import static com.yongdrel.fraudriskservice.TestData.flaggedCheck;
import static com.yongdrel.fraudriskservice.TestData.openFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelTest {

    @Nested
    class FlagLifecycle {

        /** State-transition table for manual review. */
        @ParameterizedTest(name = "{0} -> {1} allowed={2}")
        @CsvSource({
                "OPEN,UNDER_REVIEW,true", "OPEN,CONFIRMED_FRAUD,true", "OPEN,DISMISSED,true", "OPEN,OPEN,false",
                "UNDER_REVIEW,CONFIRMED_FRAUD,true", "UNDER_REVIEW,DISMISSED,true",
                "UNDER_REVIEW,OPEN,false", "UNDER_REVIEW,UNDER_REVIEW,false",
                "CONFIRMED_FRAUD,DISMISSED,false", "CONFIRMED_FRAUD,OPEN,false",
                "DISMISSED,CONFIRMED_FRAUD,false", "DISMISSED,UNDER_REVIEW,false"})
        void transitions(FlagStatus from, FlagStatus to, boolean allowed) {
            assertThat(from.canMoveTo(to)).isEqualTo(allowed);
        }

        @Test
        void raiseCopiesCheckDataAndStartsOpen() {
            RiskFlag flag = RiskFlag.raise(flaggedCheck(50L), NOW);

            assertThat(flag.getStatus()).isEqualTo(FlagStatus.OPEN);
            assertThat(flag.getCheckId()).isEqualTo(50L);
            assertThat(flag.getTenantId()).isEqualTo(TENANT);
            assertThat(flag.getReasons()).containsExactly(RuleType.DUPLICATE_PAYMENT_REFERENCE);
            assertThat(flag.getScore()).isEqualTo(70);
            assertThat(flag.getCreatedAt()).isEqualTo(NOW);
        }

        @Test
        void reviewRecordsReviewerNoteAndTime() {
            RiskFlag flag = openFlag(1L, TENANT);

            flag.review(FlagStatus.CONFIRMED_FRAUD, 3L, "  same UPI ref twice  ", NOW);

            assertThat(flag.getStatus()).isEqualTo(FlagStatus.CONFIRMED_FRAUD);
            assertThat(flag.getReviewerId()).isEqualTo(3L);
            assertThat(flag.getReviewNote()).isEqualTo("same UPI ref twice");
            assertThat(flag.getReviewedAt()).isEqualTo(NOW);
        }

        @Test
        void blankNoteIsStoredAsNull() {
            RiskFlag flag = openFlag(1L, TENANT);
            flag.review(FlagStatus.UNDER_REVIEW, 3L, "   ", NOW);
            assertThat(flag.getReviewNote()).isNull();
        }

        @Test
        void closedFlagCannotBeReviewedAgain() {
            RiskFlag flag = openFlag(1L, TENANT);
            flag.review(FlagStatus.DISMISSED, 3L, "legit", NOW);

            assertThatThrownBy(() -> flag.review(FlagStatus.CONFIRMED_FRAUD, 3L, "changed mind", NOW))
                    .isInstanceOf(InvalidFlagTransitionException.class)
                    .hasMessageContaining("DISMISSED to CONFIRMED_FRAUD");
        }
    }

    @Nested
    class RuleValidation {

        @ParameterizedTest(name = "{0} threshold={1} window={2} weight={3} rejected")
        @CsvSource({
                "DUPLICATE_PAYMENT_REFERENCE,0,0,70",
                "BUYER_VELOCITY,5,-1,25",
                "BUYER_VELOCITY,5,525601,25",
                "AMOUNT_MISMATCH,101,0,40",
                "AMOUNT_MISMATCH,-1,0,40",
                "PRIOR_CONFIRMED_FRAUD,1,0,0",
                "PRIOR_CONFIRMED_FRAUD,1,0,101"})
        void invalidRules(RuleType type, int threshold, int window, int weight) {
            assertThatThrownBy(() -> new RiskRule(null, null, type, true, threshold, window, weight, null, NOW, null))
                    .isInstanceOf(InvalidRuleException.class);
        }

        @Test
        void validBoundariesAccepted() {
            RiskRule r = new RiskRule(null, null, RuleType.AMOUNT_MISMATCH, true, 0, 525600, 100, null, NOW, null);
            assertThat(r.getDescription()).isEqualTo(RuleType.AMOUNT_MISMATCH.getDescription());
            assertThat(r.isGlobal()).isTrue();
            assertThat(r.windowStart(NOW)).isEqualTo(NOW.minus(Duration.ofMinutes(525600)));
        }

        @Test
        void updateChangesSettingsAndValidates() {
            RiskRule r = new RiskRule(7L, TENANT, RuleType.BUYER_VELOCITY, true, 5, 10, 25, "x", NOW, 0L);

            r.update(false, 3, 30, 40, "  stricter  ", NOW.plusSeconds(60));

            assertThat(r.isEnabled()).isFalse();
            assertThat(r.getThreshold()).isEqualTo(3);
            assertThat(r.getWindowMinutes()).isEqualTo(30);
            assertThat(r.getWeight()).isEqualTo(40);
            assertThat(r.getDescription()).isEqualTo("stricter");
            assertThat(r.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
            assertThat(r.isGlobal()).isFalse();
            assertThatThrownBy(() -> r.update(true, 0, 10, 25, null, NOW)).isInstanceOf(InvalidRuleException.class);
        }
    }

    @Nested
    class Normalization {

        @Test
        void referenceIgnoresCaseSpacesAndHyphens() {
            assertThat(TransactionDetails.normalizeReference(" txn 12-34 ab ")).isEqualTo("TXN1234AB");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", " - "})
        void emptyReferenceBecomesNull(String value) {
            assertThat(TransactionDetails.normalizeReference(value)).isNull();
        }

        @Test
        void hashIsLowerCased() {
            assertThat(TransactionDetails.normalizeHash(" ABC ")).isEqualTo("abc");
            assertThat(TransactionDetails.normalizeHash(" ")).isNull();
        }

        @Test
        void requiredIdsAreEnforced() {
            assertThatThrownBy(() -> new TransactionDetails(null, 1L, 1L, 1L, "R", null, BigDecimal.ONE, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Roles {

        @ParameterizedTest(name = "claim {0} -> {1}")
        @CsvSource({"USER,STUDENT", "student,STUDENT", "ROLE_ORGANIZER,ORGANIZER", "Admin,ADMIN", "SERVICE,SERVICE"})
        void knownRoles(String claim, Role expected) {
            assertThat(Role.fromClaim(claim)).contains(expected);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"HACKER", "ROLE_"})
        void unknownRolesAreEmpty(String claim) {
            assertThat(Role.fromClaim(claim)).isEmpty();
        }

        @Test
        void actorHelpers() {
            Actor organizer = new Actor(3L, Role.ORGANIZER, TENANT);
            assertThat(organizer.belongsToTenant(TENANT)).isTrue();
            assertThat(organizer.belongsToTenant(99L)).isFalse();
            assertThat(organizer.hasPlatformWideAccess()).isFalse();
            assertThat(new Actor(1L, Role.SERVICE, null).hasPlatformWideAccess()).isTrue();
            assertThat(new Actor(1L, Role.ADMIN, null).belongsToTenant(TENANT)).isFalse();
        }
    }
}
