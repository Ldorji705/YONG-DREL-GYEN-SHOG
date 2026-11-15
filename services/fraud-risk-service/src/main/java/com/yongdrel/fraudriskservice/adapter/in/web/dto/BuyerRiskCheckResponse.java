package com.yongdrel.fraudriskservice.adapter.in.web.dto;

import com.yongdrel.fraudriskservice.domain.model.Decision;
import com.yongdrel.fraudriskservice.domain.model.RiskCheck;

/** What a student sees: the outcome only, never which rules fired (so the checks cannot be gamed). */
public record BuyerRiskCheckResponse(Long id, Long orderId, Decision decision) {

    public static BuyerRiskCheckResponse from(RiskCheck c) {
        return new BuyerRiskCheckResponse(c.id(), c.transaction().orderId(), c.assessment().decision());
    }
}
