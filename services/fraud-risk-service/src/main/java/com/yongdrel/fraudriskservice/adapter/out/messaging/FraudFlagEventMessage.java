package com.yongdrel.fraudriskservice.adapter.out.messaging;

import java.time.Instant;
import java.util.List;

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
