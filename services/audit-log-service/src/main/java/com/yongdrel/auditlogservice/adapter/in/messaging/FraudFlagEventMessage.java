package com.yongdrel.auditlogservice.adapter.in.messaging;

import java.time.Instant;
import java.util.List;

/** Contract of the Fraud &amp; Risk Service's flag events (fraud.flag.raised / fraud.flag.reviewed). */
public record FraudFlagEventMessage(
        String eventType,
        Long flagId,
        Long checkId,
        Long tenantId,
        Long eventId,
        Long orderId,
        Long buyerId,
        Integer score,
        String riskLevel,
        List<String> reasons,
        String status,
        Long reviewerId,
        Instant occurredAt
) {
}
