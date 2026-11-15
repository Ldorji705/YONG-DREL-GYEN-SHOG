package com.yongdrel.fraudriskservice.application.port.out;

import com.yongdrel.fraudriskservice.domain.model.RiskFlag;

public interface FlagEventPublisherPort {
    void flagRaised(RiskFlag flag);
    void flagReviewed(RiskFlag flag);
}
