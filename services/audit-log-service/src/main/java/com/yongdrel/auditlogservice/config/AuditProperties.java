package com.yongdrel.auditlogservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** The 20 platform services (plus fraud-risk-service) that may appear as sourceService. */
@ConfigurationProperties(prefix = "app.audit")
public record AuditProperties(
        @DefaultValue({"api-gateway", "auth-service", "user-profile-service", "tenant-service", "event-service",
                "ticket-inventory-service", "prize-catalog-service", "order-service", "payment-qr-service",
                "payment-proof-service", "payment-verification-service", "bulk-verification-service",
                "draw-service", "winner-service", "fairness-verification-service", "notification-service",
                "email-template-service", "audit-log-service", "analytics-service", "scheduler-service",
                "fraud-risk-service"})
        List<String> knownSources
) {
}
