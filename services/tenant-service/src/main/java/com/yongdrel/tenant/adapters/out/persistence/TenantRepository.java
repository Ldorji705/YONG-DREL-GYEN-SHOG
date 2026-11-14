package com.yongdrel.tenant.adapters.out.persistence;

import com.yongdrel.tenant.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    List<Tenant> findAllByOwnerUserId(UUID ownerUserId);

    Optional<Tenant> findByTenantIdAndOwnerUserId(
            UUID tenantId,
            UUID ownerUserId
    );

    boolean existsByNameIgnoreCaseAndOwnerUserId(
            String name,
            UUID ownerUserId
    );
}