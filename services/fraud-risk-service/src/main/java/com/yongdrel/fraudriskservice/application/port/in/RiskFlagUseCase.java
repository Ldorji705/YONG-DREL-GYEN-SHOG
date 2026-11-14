package com.yongdrel.fraudriskservice.application.port.in;

import com.yongdrel.fraudriskservice.application.model.FlagSearchCriteria;
import com.yongdrel.fraudriskservice.application.model.PageResult;
import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;

public interface RiskFlagUseCase {

    PageResult<RiskFlag> search(FlagSearchCriteria criteria, int page, int size, Actor actor);

    RiskFlag get(Long flagId, Actor actor);

    RiskFlag review(Long flagId, FlagStatus status, String note, Actor actor);
}
