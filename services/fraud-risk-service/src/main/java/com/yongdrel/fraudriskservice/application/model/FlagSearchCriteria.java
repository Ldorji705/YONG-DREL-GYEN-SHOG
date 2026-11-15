package com.yongdrel.fraudriskservice.application.model;

import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskLevel;

/** Optional filters for listing flags; null means "any". */
public record FlagSearchCriteria(Long tenantId, Long eventId, Long orderId, Long buyerId,
                                 FlagStatus status, RiskLevel riskLevel) {

    public FlagSearchCriteria withTenantId(Long newTenantId) {
        return new FlagSearchCriteria(newTenantId, eventId, orderId, buyerId, status, riskLevel);
    }
}
