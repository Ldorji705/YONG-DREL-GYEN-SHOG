package com.yongdrel.payment_service.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentOutboxRepository
        extends JpaRepository<PaymentOutboxEntity, UUID> {

    List<PaymentOutboxEntity> findTop20ByPublishedFalseOrderByCreatedAtAsc();
}
