package com.yongdrel.lotterydraw.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DrawWinnerRepository
        extends JpaRepository<DrawWinnerEntity, UUID> {

    List<DrawWinnerEntity> findByDraw_IdOrderBySelectionOrderAsc(UUID drawId);
}
