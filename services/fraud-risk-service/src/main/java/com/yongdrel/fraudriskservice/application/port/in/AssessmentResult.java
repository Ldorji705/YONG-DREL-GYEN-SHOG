package com.yongdrel.fraudriskservice.application.port.in;

import com.yongdrel.fraudriskservice.domain.model.RiskCheck;

/** created == false means an identical submission was already checked and is returned as-is. */
public record AssessmentResult(RiskCheck check, boolean created) {
}
