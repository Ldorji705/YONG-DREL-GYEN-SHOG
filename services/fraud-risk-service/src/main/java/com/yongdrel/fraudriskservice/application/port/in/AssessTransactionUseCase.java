package com.yongdrel.fraudriskservice.application.port.in;

import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.RiskCheck;

public interface AssessTransactionUseCase {

    AssessmentResult assess(AssessTransactionCommand command, Actor actor);

    RiskCheck getCheck(Long checkId, Actor actor);
}
