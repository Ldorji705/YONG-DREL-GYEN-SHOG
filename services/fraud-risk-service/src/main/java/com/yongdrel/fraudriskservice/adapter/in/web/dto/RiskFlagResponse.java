package com.yongdrel.fraudriskservice.adapter.in.web.dto;

import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;
import com.yongdrel.fraudriskservice.domain.model.RiskLevel;
import com.yongdrel.fraudriskservice.domain.model.RuleType;

import java.time.Instant;
import java.util.List;

public record RiskFlagResponse(
        Long id,
        Long checkId,
        Long tenantId,
        Long eventId,
        Long orderId,
        Long buyerId,
        int score,
        RiskLevel riskLevel,
        List<RuleType> reasons,
        FlagStatus status,
        Long reviewerId,
        String reviewNote,
        Instant createdAt,
        Instant reviewedAt
) {

    public static RiskFlagResponse from(RiskFlag f) {
        return new RiskFlagResponse(f.getId(), f.getCheckId(), f.getTenantId(), f.getEventId(), f.getOrderId(),
                f.getBuyerId(), f.getScore(), f.getRiskLevel(), f.getReasons(), f.getStatus(), f.getReviewerId(),
                f.getReviewNote(), f.getCreatedAt(), f.getReviewedAt());
    }
}
