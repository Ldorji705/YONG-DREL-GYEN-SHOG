package com.yongdrel.fraudriskservice.adapter.in.web.dto;

import com.yongdrel.fraudriskservice.domain.model.Decision;
import com.yongdrel.fraudriskservice.domain.model.RiskCheck;
import com.yongdrel.fraudriskservice.domain.model.RiskLevel;
import com.yongdrel.fraudriskservice.domain.model.RuleFinding;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RiskCheckResponse(
        Long id,
        Long tenantId,
        Long eventId,
        Long orderId,
        Long buyerId,
        String paymentReference,
        BigDecimal amount,
        BigDecimal expectedAmount,
        int score,
        RiskLevel riskLevel,
        Decision decision,
        List<RuleFinding> findings,
        Long flagId,
        Instant createdAt
) {

    public static RiskCheckResponse from(RiskCheck c) {
        var tx = c.transaction();
        var a = c.assessment();
        return new RiskCheckResponse(c.id(), tx.tenantId(), tx.eventId(), tx.orderId(), tx.buyerId(),
                tx.paymentReference(), tx.amount(), tx.expectedAmount(), a.score(), a.level(), a.decision(),
                a.findings(), c.flagId(), c.createdAt());
    }
}
