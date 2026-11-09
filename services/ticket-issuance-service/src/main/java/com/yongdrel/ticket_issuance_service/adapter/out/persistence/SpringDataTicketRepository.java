package com.yongdrel.ticket_issuance_service.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataTicketRepository
        extends JpaRepository<TicketEntity, UUID> {

    List<TicketEntity> findByUserId(Long userId);

    List<TicketEntity> findByOrderId(UUID orderId);

    List<TicketEntity> findByEventId(UUID eventId);

    boolean existsByOrderIdAndTicketIndex(
            UUID orderId,
            Integer ticketIndex
    );
}
