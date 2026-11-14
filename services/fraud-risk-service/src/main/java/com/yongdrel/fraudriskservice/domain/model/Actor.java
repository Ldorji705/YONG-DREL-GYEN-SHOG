package com.yongdrel.fraudriskservice.domain.model;

import java.util.Objects;

/** The authenticated caller, extracted from the JWT. tenantId is required for organizers. */
public record Actor(Long userId, Role role, Long tenantId) {

    public Actor {
        Objects.requireNonNull(role, "role");
    }

    public boolean is(Role candidate) {
        return role == candidate;
    }

    public boolean hasPlatformWideAccess() {
        return role == Role.ADMIN || role == Role.SERVICE;
    }

    public boolean belongsToTenant(Long candidateTenantId) {
        return tenantId != null && tenantId.equals(candidateTenantId);
    }
}
