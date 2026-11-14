package com.yongdrel.fraudriskservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.references.order-service")
public record OrderServiceProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8082") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout
) {
}
