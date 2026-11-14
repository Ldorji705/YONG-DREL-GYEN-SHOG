package com.yongdrel.fraudriskservice.adapter.in.web;

import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.Role;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a validated JWT into an Actor.
 * Claims: sub = numeric user id, role = STUDENT|USER|ORGANIZER|ADMIN|SERVICE, tenantId = organizer's tenant.
 */
public final class JwtActorMapper {

    public static final String ROLE_CLAIM = "role";
    public static final String TENANT_CLAIM = "tenantId";

    private JwtActorMapper() {
    }

    public static Actor toActor(Jwt jwt) {
        Role role = Role.fromClaim(jwt.getClaimAsString(ROLE_CLAIM))
                .orElseThrow(() -> new ForbiddenOperationException("Token has no recognised role"));
        Long userId = parseLong(jwt.getSubject());
        if (userId == null || userId <= 0) {
            // Defence in depth: JwtDecoderConfig already rejects such tokens with 401.
            throw new ForbiddenOperationException("Token subject must be a numeric user id");
        }
        return new Actor(userId, role, parseLong(jwt.getClaimAsString(TENANT_CLAIM)));
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
