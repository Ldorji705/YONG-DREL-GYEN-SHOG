package com.yongdrel.fraudriskservice.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Manual-review lifecycle of a flag.
 * OPEN -> UNDER_REVIEW -> CONFIRMED_FRAUD | DISMISSED (OPEN may also close directly).
 */
public enum FlagStatus {
    OPEN, UNDER_REVIEW, CONFIRMED_FRAUD, DISMISSED;

    public boolean isTerminal() {
        return this == CONFIRMED_FRAUD || this == DISMISSED;
    }

    public boolean canMoveTo(FlagStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<FlagStatus> allowedTargets() {
        return switch (this) {
            case OPEN -> EnumSet.of(UNDER_REVIEW, CONFIRMED_FRAUD, DISMISSED);
            case UNDER_REVIEW -> EnumSet.of(CONFIRMED_FRAUD, DISMISSED);
            case CONFIRMED_FRAUD, DISMISSED -> EnumSet.noneOf(FlagStatus.class);
        };
    }
}
