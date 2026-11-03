package com.yongdrel.tenant.application.service;

import com.yongdrel.tenant.adapters.out.persistence.TenantRepository;
import com.yongdrel.tenant.application.exception.DuplicateTenantException;
import com.yongdrel.tenant.application.exception.TenantNotFoundException;
import com.yongdrel.tenant.domain.model.Tenant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("ACTIVE", "INACTIVE", "SUSPENDED");

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    // Create a tenant for the authenticated owner.
    public Tenant createTenant(String name, UUID ownerUserId) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant name is required"
            );
        }

        String tenantName = name.trim();

        if (tenantRepository.existsByNameIgnoreCaseAndOwnerUserId(
                tenantName, ownerUserId)) {

            throw new DuplicateTenantException(
                    "You already have a tenant with this name"
            );
        }

        Tenant tenant = new Tenant();
        tenant.setName(tenantName);
        tenant.setOwnerUserId(ownerUserId);
        tenant.setStatus("ACTIVE");

        return tenantRepository.save(tenant);
    }

    // Get a tenant only if it belongs to the authenticated owner.
    @Transactional(readOnly = true)
    public Tenant getTenant(UUID tenantId, UUID ownerUserId) {

        return tenantRepository
                .findByTenantIdAndOwnerUserId(tenantId, ownerUserId)
                .orElseThrow(() ->
                        new TenantNotFoundException("Tenant not found")
                );
    }

    // Get all tenants belonging to the authenticated owner.
    @Transactional(readOnly = true)
    public List<Tenant> getMyTenants(UUID ownerUserId) {

        return tenantRepository.findAllByOwnerUserId(ownerUserId);
    }

    // Update a tenant's name, provided it belongs to the owner.
    public Tenant updateTenant(
            UUID tenantId,
            String name,
            UUID ownerUserId
    ) {

        Tenant tenant = getTenant(tenantId, ownerUserId);

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant name is required"
            );
        }

        String updatedName = name.trim();

        if (!tenant.getName().equalsIgnoreCase(updatedName)
                && tenantRepository.existsByNameIgnoreCaseAndOwnerUserId(
                        updatedName, ownerUserId)) {

            throw new DuplicateTenantException(
                    "You already have a tenant with this name"
            );
        }

        tenant.setName(updatedName);

        return tenantRepository.save(tenant);
    }

    // Change tenant status.
    // Authorization is enforced in SecurityConfig.
    public Tenant updateTenantStatus(
            UUID tenantId,
            String status
    ) {

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant status is required"
            );
        }

        String updatedStatus =
                status.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_STATUSES.contains(updatedStatus)) {
            throw new IllegalArgumentException(
                    "Invalid tenant status. Allowed values: ACTIVE, INACTIVE, SUSPENDED"
            );
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() ->
                        new TenantNotFoundException("Tenant not found")
                );

        tenant.setStatus(updatedStatus);

        return tenantRepository.save(tenant);
    }
}
