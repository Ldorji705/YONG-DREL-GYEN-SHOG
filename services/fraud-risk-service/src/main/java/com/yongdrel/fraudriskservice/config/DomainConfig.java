package com.yongdrel.fraudriskservice.config;

import com.yongdrel.fraudriskservice.domain.service.RiskEvaluator;
import com.yongdrel.fraudriskservice.domain.service.ScoringPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the framework-free domain services into Spring. */
@Configuration
public class DomainConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RiskEvaluator riskEvaluator(FraudProperties props) {
        return new RiskEvaluator(new ScoringPolicy(props.flagThreshold(), props.mediumScore(), props.highScore()));
    }
}
