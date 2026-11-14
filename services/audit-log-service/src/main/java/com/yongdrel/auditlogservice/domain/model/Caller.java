package com.yongdrel.auditlogservice.domain.model;

import java.util.Objects;

/** The authenticated API caller (from the JWT). Not to be confused with the audit event's actor. */
public record Caller(Long userId, Role role, Long tenantId) {

    public Caller {
        Objects.requireNonNull(role, "role");
    }

    public boolean is(Role candidate) {
        return role == candidate;
    }
}
