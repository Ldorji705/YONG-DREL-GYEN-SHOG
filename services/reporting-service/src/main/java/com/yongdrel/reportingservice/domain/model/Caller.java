package com.yongdrel.reportingservice.domain.model;

import java.util.Objects;

/** The authenticated caller from the JWT: numeric user id, role, tenant (organizers). */
public record Caller(Long userId, Role role, Long tenantId) {

    public Caller {
        Objects.requireNonNull(role, "role");
    }

    public boolean is(Role candidate) {
        return role == candidate;
    }
}
