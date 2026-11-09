
package com.yongdrel.event.adapter.out.persistence;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository
        extends JpaRepository<EventEntity, UUID> {

    // Atomically reserve tickets without exceeding capacity
    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true
    )
    @Query("""
        UPDATE EventEntity e
        SET e.reservedTickets = e.reservedTickets + :quantity
        WHERE e.id = :eventId
          AND e.status =
              com.yongdrel.event.domain.model.EventStatus.ACTIVE
          AND e.reservedTickets <= e.maxTickets - :quantity
    """)
    int reserveCapacity(
            @Param("eventId") UUID eventId,
            @Param("quantity") int quantity
    );

    // Lock event row when updating capacity or deleting
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT e
        FROM EventEntity e
        WHERE e.id = :id
    """)
    Optional<EventEntity> findByIdForUpdate(
            @Param("id") UUID id
    );
}
