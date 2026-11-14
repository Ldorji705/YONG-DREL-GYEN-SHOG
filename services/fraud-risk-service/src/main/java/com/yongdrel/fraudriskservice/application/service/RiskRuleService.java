package com.yongdrel.fraudriskservice.application.service;

import com.yongdrel.fraudriskservice.application.exception.ConflictException;
import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.port.in.RiskRuleUseCase;
import com.yongdrel.fraudriskservice.application.port.in.RuleSettings;
import com.yongdrel.fraudriskservice.application.port.out.RiskRuleRepositoryPort;
import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.Role;
import com.yongdrel.fraudriskservice.domain.model.RuleType;
import com.yongdrel.fraudriskservice.domain.service.EffectiveRules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class RiskRuleService implements RiskRuleUseCase {

    private final RiskRuleRepositoryPort rules;
    private final Clock clock;

    public RiskRuleService(RiskRuleRepositoryPort rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskRule> list(Long tenantId, Actor actor) {
        AccessPolicy.requireRuleReader(actor, tenantId);
        return tenantId == null ? rules.findGlobal() : rules.findByTenant(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskRule> effective(Long tenantId, Actor actor) {
        Long scope = tenantId == null && actor.is(Role.ORGANIZER) ? actor.tenantId() : tenantId;
        AccessPolicy.requireRuleReader(actor, scope);
        if (scope == null) {
            return rules.findGlobal();
        }
        return EffectiveRules.merge(rules.findGlobal(), rules.findByTenant(scope));
    }

    @Override
    @Transactional(readOnly = true)
    public RiskRule get(Long ruleId, Actor actor) {
        RiskRule rule = find(ruleId);
        AccessPolicy.requireRuleReader(actor, rule.getTenantId());
        return rule;
    }

    @Override
    @Transactional
    public RiskRule create(Long tenantId, RuleType ruleType, RuleSettings s, Actor actor) {
        AccessPolicy.requireRuleManager(actor, tenantId);
        if (rules.exists(tenantId, ruleType)) {
            throw new ConflictException(ruleType + " rule already exists for "
                    + (tenantId == null ? "platform defaults" : "tenant " + tenantId)
                    + "; update it instead");
        }
        RiskRule rule = new RiskRule(null, tenantId, ruleType, s.enabled(), s.threshold(),
                s.windowMinutes(), s.weight(), s.description(), clock.instant(), null);
        return rules.save(rule);
    }

    @Override
    @Transactional
    public RiskRule update(Long ruleId, RuleSettings s, Actor actor) {
        RiskRule rule = find(ruleId);
        AccessPolicy.requireRuleManager(actor, rule.getTenantId());
        rule.update(s.enabled(), s.threshold(), s.windowMinutes(), s.weight(), s.description(), clock.instant());
        return rules.save(rule);
    }

    @Override
    @Transactional
    public void delete(Long ruleId, Actor actor) {
        RiskRule rule = find(ruleId);
        AccessPolicy.requireRuleManager(actor, rule.getTenantId());
        rules.deleteById(ruleId);
    }

    private RiskRule find(Long ruleId) {
        return rules.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule " + ruleId + " not found"));
    }
}
