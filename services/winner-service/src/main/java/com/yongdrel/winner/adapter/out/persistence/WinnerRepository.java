package com.yongdrel.winner.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WinnerRepository extends JpaRepository<WinnerEntity, UUID> {

    List<WinnerEntity> findByEventId(UUID eventId);

    List<WinnerEntity> findByUserId(String userId);

    boolean existsByDrawIdAndTicketId(String drawId, String ticketId);
}
