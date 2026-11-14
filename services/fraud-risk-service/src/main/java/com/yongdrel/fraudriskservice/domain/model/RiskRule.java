package com.yongdrel.fraudriskservice.domain.model;

import com.yongdrel.fraudriskservice.domain.exception.InvalidRuleException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A configurable check. tenantId == null means a platform-wide default;
 * a tenant rule of the same type overrides the default for that organizer.
 */
public class RiskRule {

    public static final int MAX_WINDOW_MINUTES = 525_600; // one year

    private final Long id;
    private final Long tenantId;
    private final RuleType ruleType;
    private boolean enabled;
    private int threshold;
    private int windowMinutes;
    private int weight;
    private String description;
    private Instant updatedAt;
    private final Long version;

    public RiskRule(Long id, Long tenantId, RuleType ruleType, boolean enabled, int threshold,
                    int windowMinutes, int weight, String description, Instant updatedAt, Long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.ruleType = Objects.requireNonNull(ruleType, "ruleType");
        this.version = version;
        apply(enabled, threshold, windowMinutes, weight, description, updatedAt);
    }

    public void update(boolean newEnabled, int newThreshold, int newWindowMinutes, int newWeight,
                       String newDescription, Instant now) {
        apply(newEnabled, newThreshold, newWindowMinutes, newWeight, newDescription, now);
    }

    private void apply(boolean newEnabled, int newThreshold, int newWindowMinutes, int newWeight,
                       String newDescription, Instant now) {
        validate(ruleType, newThreshold, newWindowMinutes, newWeight);
        this.enabled = newEnabled;
        this.threshold = newThreshold;
        this.windowMinutes = newWindowMinutes;
        this.weight = newWeight;
        this.description = newDescription == null || newDescription.isBlank()
                ? ruleType.getDescription()
                : newDescription.trim();
        this.updatedAt = now;
    }

    static void validate(RuleType type, int threshold, int windowMinutes, int weight) {
        if (type.isCountBased() && threshold < 1) {
            throw new InvalidRuleException(type + " threshold must be at least 1");
        }
        if (!type.isCountBased() && (threshold < 0 || threshold > 100)) {
            throw new InvalidRuleException(type + " threshold is a percentage and must be between 0 and 100");
        }
        if (windowMinutes < 0 || windowMinutes > MAX_WINDOW_MINUTES) {
            throw new InvalidRuleException("windowMinutes must be between 0 and " + MAX_WINDOW_MINUTES);
        }
        if (weight < 1 || weight > 100) {
            throw new InvalidRuleException("weight must be between 1 and 100");
        }
    }

    /** Start of the look-back window; a window of 0 means "all history". */
    public Instant windowStart(Instant now) {
        return windowMinutes == 0 ? Instant.EPOCH : now.minus(Duration.ofMinutes(windowMinutes));
    }

    public boolean isGlobal() {
        return tenantId == null;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public RuleType getRuleType() { return ruleType; }
    public boolean isEnabled() { return enabled; }
    public int getThreshold() { return threshold; }
    public int getWindowMinutes() { return windowMinutes; }
    public int getWeight() { return weight; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
