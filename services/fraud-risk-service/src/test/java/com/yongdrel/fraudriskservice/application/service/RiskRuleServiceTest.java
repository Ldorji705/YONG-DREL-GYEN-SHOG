package com.yongdrel.fraudriskservice.application.service;

import com.yongdrel.fraudriskservice.application.exception.ConflictException;
import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.port.in.RuleSettings;
import com.yongdrel.fraudriskservice.application.port.out.RiskRuleRepositoryPort;
import com.yongdrel.fraudriskservice.domain.exception.InvalidRuleException;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.yongdrel.fraudriskservice.TestData.ADMIN;
import static com.yongdrel.fraudriskservice.TestData.NOW;
import static com.yongdrel.fraudriskservice.TestData.ORGANIZER;
import static com.yongdrel.fraudriskservice.TestData.OTHER_TENANT;
import static com.yongdrel.fraudriskservice.TestData.SERVICE;
import static com.yongdrel.fraudriskservice.TestData.STUDENT;
import static com.yongdrel.fraudriskservice.TestData.TENANT;
import static com.yongdrel.fraudriskservice.TestData.defaultRules;
import static com.yongdrel.fraudriskservice.TestData.rule;
import static com.yongdrel.fraudriskservice.TestData.tenantRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskRuleServiceTest {

    @Mock RiskRuleRepositoryPort rules;

    private RiskRuleService service;
    private final RuleSettings settings = new RuleSettings(true, 3, 15, 30, "stricter velocity");

    @BeforeEach
    void setUp() {
        service = new RiskRuleService(rules, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void organizerCreatesOverrideForOwnTenant() {
        when(rules.exists(TENANT, RuleType.BUYER_VELOCITY)).thenReturn(false);
        when(rules.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskRule created = service.create(TENANT, RuleType.BUYER_VELOCITY, settings, ORGANIZER);

        assertThat(created.getTenantId()).isEqualTo(TENANT);
        assertThat(created.getThreshold()).isEqualTo(3);
        assertThat(created.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void duplicateRuleIsConflict() {
        when(rules.exists(TENANT, RuleType.BUYER_VELOCITY)).thenReturn(true);

        assertThatThrownBy(() -> service.create(TENANT, RuleType.BUYER_VELOCITY, settings, ORGANIZER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("tenant 10");
        verify(rules, never()).save(any());
    }

    @Test
    void duplicateGlobalRuleIsConflict() {
        when(rules.exists(null, RuleType.BUYER_VELOCITY)).thenReturn(true);
        assertThatThrownBy(() -> service.create(null, RuleType.BUYER_VELOCITY, settings, ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("platform defaults");
    }

    @Test
    void organizerCannotCreateGlobalOrOtherTenantRules() {
        assertThatThrownBy(() -> service.create(null, RuleType.BUYER_VELOCITY, settings, ORGANIZER))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.create(OTHER_TENANT, RuleType.BUYER_VELOCITY, settings, ORGANIZER))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void studentsAndServicesCannotManageOrReadRules() {
        assertThatThrownBy(() -> service.create(TENANT, RuleType.BUYER_VELOCITY, settings, STUDENT))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.list(null, SERVICE)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void invalidSettingsAreRejectedByDomain() {
        when(rules.exists(TENANT, RuleType.DUPLICATE_PAYMENT_REFERENCE)).thenReturn(false);
        RuleSettings bad = new RuleSettings(true, 0, 0, 70, null);

        assertThatThrownBy(() -> service.create(TENANT, RuleType.DUPLICATE_PAYMENT_REFERENCE, bad, ORGANIZER))
                .isInstanceOf(InvalidRuleException.class);
    }

    @Test
    void listReturnsGlobalOrTenantRules() {
        when(rules.findGlobal()).thenReturn(defaultRules());
        when(rules.findByTenant(TENANT)).thenReturn(List.of(tenantRule(9L, TENANT, RuleType.BUYER_VELOCITY)));

        assertThat(service.list(null, ORGANIZER)).hasSize(5);
        assertThat(service.list(TENANT, ORGANIZER)).hasSize(1);
        assertThatThrownBy(() -> service.list(OTHER_TENANT, ORGANIZER))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void effectiveDefaultsToOrganizersTenant() {
        when(rules.findGlobal()).thenReturn(defaultRules());
        when(rules.findByTenant(TENANT)).thenReturn(List.of(tenantRule(9L, TENANT, RuleType.BUYER_VELOCITY)));

        List<RiskRule> effective = service.effective(null, ORGANIZER);

        assertThat(effective).hasSize(5);
        assertThat(effective).filteredOn(r -> r.getRuleType() == RuleType.BUYER_VELOCITY)
                .singleElement().extracting(RiskRule::getId).isEqualTo(9L);
    }

    @Test
    void adminEffectiveWithoutTenantIsDefaults() {
        when(rules.findGlobal()).thenReturn(defaultRules());
        assertThat(service.effective(null, ADMIN)).hasSize(5);
    }

    @Test
    void updateAndDeleteOwnTenantRule() {
        RiskRule existing = tenantRule(9L, TENANT, RuleType.BUYER_VELOCITY);
        when(rules.findById(9L)).thenReturn(Optional.of(existing));
        when(rules.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskRule updated = service.update(9L, settings, ORGANIZER);
        service.delete(9L, ORGANIZER);

        assertThat(updated.getWindowMinutes()).isEqualTo(15);
        assertThat(service.get(9L, ORGANIZER).getId()).isEqualTo(9L);
        verify(rules).deleteById(9L);
    }

    @Test
    void organizerCannotChangeGlobalRule() {
        RiskRule global = new RiskRule(1L, null, RuleType.BUYER_VELOCITY, true, 5, 10, 25, null, NOW, 0L);
        when(rules.findById(1L)).thenReturn(Optional.of(global));

        assertThatThrownBy(() -> service.update(1L, settings, ORGANIZER))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.delete(1L, ORGANIZER))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(rules, never()).deleteById(any());
    }

    @Test
    void adminCanChangeGlobalRule() {
        when(rules.findById(1L)).thenReturn(Optional.of(rule(RuleType.BUYER_VELOCITY, 5, 10, 25)));
        when(rules.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, settings, ADMIN).getThreshold()).isEqualTo(3);
    }

    @Test
    void unknownRuleIs404() {
        when(rules.findById(77L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(77L, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
    }
}
