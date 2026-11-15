package com.yongdrel.fraudriskservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Scoring thresholds; can be overridden centrally in config-repo/fraud-risk-service.properties. */
@ConfigurationProperties(prefix = "app.fraud")
public record FraudProperties(
        @DefaultValue("30") int flagThreshold,
        @DefaultValue("30") int mediumScore,
        @DefaultValue("70") int highScore
) {
}
