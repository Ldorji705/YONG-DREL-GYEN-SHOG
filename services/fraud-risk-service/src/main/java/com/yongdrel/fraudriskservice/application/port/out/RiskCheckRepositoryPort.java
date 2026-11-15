package com.yongdrel.fraudriskservice.application.port.out;

import com.yongdrel.fraudriskservice.domain.model.RiskCheck;
import java.time.Instant;
import java.util.Optional;

public interface RiskCheckRepositoryPort {
    Optional<RiskCheck> findByIdempotencyKey(String key);
    RiskCheck save(RiskCheck check);
    Optional<RiskCheck> findById(Long id);
    long countOtherOrdersWithReference(Long tenantId, String reference, Long excludeOrderId, Instant since);
    long countOtherOrdersWithProofHash(Long tenantId, String hash, Long excludeOrderId, Instant since);
    long countBuyerChecksSince(Long tenantId, Long buyerId, Instant since);
}
