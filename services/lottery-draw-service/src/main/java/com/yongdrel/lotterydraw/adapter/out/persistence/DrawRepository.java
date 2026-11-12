package com.yongdrel.lotterydraw.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DrawRepository extends JpaRepository<DrawEntity, UUID> {

    Optional<DrawEntity> findByEventId(UUID eventId);

    boolean existsByEventId(UUID eventId);
}
