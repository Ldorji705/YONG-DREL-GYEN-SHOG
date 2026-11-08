package com.yongdrel.ticket_issuance_service.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
