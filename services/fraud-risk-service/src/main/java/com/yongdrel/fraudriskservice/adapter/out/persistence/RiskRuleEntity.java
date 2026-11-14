package com.yongdrel.fraudriskservice.adapter.out.persistence;

import com.yongdrel.fraudriskservice.domain.model.RiskRule;
import com.yongdrel.fraudriskservice.domain.model.RuleType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "risk_rules")
public class RiskRuleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id") private Long tenantId;
    @Enumerated(EnumType.STRING) @Column(name = "rule_type", nullable = false, length = 50)
    private RuleType ruleType;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private int threshold;
    @Column(name = "window_minutes", nullable = false) private int windowMinutes;
    @Column(nullable = false) private int weight;
    @Column(length = 255) private String description;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected RiskRuleEntity() {
    }

    static RiskRuleEntity from(RiskRule value) {
        RiskRuleEntity entity = new RiskRuleEntity();
        entity.id = value.getId();
        entity.tenantId = value.getTenantId();
        entity.ruleType = value.getRuleType();
        entity.enabled = value.isEnabled();
        entity.threshold = value.getThreshold();
        entity.windowMinutes = value.getWindowMinutes();
        entity.weight = value.getWeight();
        entity.description = value.getDescription();
        entity.updatedAt = value.getUpdatedAt();
        entity.version = value.getVersion();
        return entity;
    }

    RiskRule toDomain() {
        return new RiskRule(id, tenantId, ruleType, enabled, threshold, windowMinutes, weight, description,
                updatedAt, version);
    }
}
