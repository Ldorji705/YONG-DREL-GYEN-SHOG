package com.yongdrel.fraudriskservice.application.port.out;

import com.yongdrel.fraudriskservice.application.model.FlagSearchCriteria;
import com.yongdrel.fraudriskservice.application.model.PageResult;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;
import java.util.Optional;

public interface RiskFlagRepositoryPort {
    RiskFlag save(RiskFlag flag);
    PageResult<RiskFlag> search(FlagSearchCriteria criteria, int page, int size);
    Optional<RiskFlag> findById(Long id);
    long countByBuyerAndStatus(Long tenantId, Long buyerId, FlagStatus status);
}
