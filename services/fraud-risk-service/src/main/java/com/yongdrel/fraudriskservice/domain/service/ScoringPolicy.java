package com.yongdrel.fraudriskservice.domain.service;

import com.yongdrel.fraudriskservice.domain.model.RiskLevel;

/**
 * Turns a 0-100 score into a level and decides when a flag is raised.
 * Defaults: flag at 30, MEDIUM from 30, HIGH from 70.
 */
public record ScoringPolicy(int flagThreshold, int mediumScore, int highScore) {

    public ScoringPolicy {
        if (mediumScore < 1 || highScore < mediumScore || highScore > 100) {
            throw new IllegalArgumentException("Require 1 <= mediumScore <= highScore <= 100");
        }
        if (flagThreshold < 1 || flagThreshold > 100) {
            throw new IllegalArgumentException("flagThreshold must be between 1 and 100");
        }
    }

    public static ScoringPolicy defaults() {
        return new ScoringPolicy(30, 30, 70);
    }

    public RiskLevel levelFor(int score) {
        if (score <= 0) {
            return RiskLevel.NONE;
        }
        if (score >= highScore) {
            return RiskLevel.HIGH;
        }
        if (score >= mediumScore) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    public boolean requiresReview(int score) {
        return score >= flagThreshold;
    }
}
