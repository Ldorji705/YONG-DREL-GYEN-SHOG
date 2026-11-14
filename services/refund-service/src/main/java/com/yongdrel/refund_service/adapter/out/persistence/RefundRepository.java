package com.yongdrel.refund_service.adapter.out.persistence;

import com.yongdrel.refund_service.domain.model.Refund;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Plain Repository (no delete methods): refunds are never deleted. */
public interface RefundRepository extends Repository<Refund, UUID> {

    Refund save(Refund refund);

    Optional<Refund> findById(UUID id);

    Optional<Refund> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
