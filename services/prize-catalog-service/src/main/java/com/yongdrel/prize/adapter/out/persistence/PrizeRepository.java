package com.yongdrel.prize.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PrizeRepository extends JpaRepository<PrizeEntity, UUID> {
}
