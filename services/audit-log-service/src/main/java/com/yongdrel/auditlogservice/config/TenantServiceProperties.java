package com.yongdrel.auditlogservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.references.tenant-service")
public record TenantServiceProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8083") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout
) {
}
