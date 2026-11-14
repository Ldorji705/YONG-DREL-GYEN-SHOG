package com.yongdrel.reportingservice.adapter.in.web;

import com.yongdrel.reportingservice.application.exception.ForbiddenOperationException;
import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.Role;

import org.springframework.security.oauth2.jwt.Jwt;

/** sub = numeric user id (already enforced by JwtDecoderConfig), role, tenantId (organizers). */
public final class JwtCallerMapper {

    public static final String ROLE_CLAIM = "role";
    public static final String TENANT_CLAIM = "tenantId";

    private JwtCallerMapper() {
    }

    public static Caller toCaller(Jwt jwt) {
        Role role = Role.fromClaim(jwt.getClaimAsString(ROLE_CLAIM))
                .orElseThrow(() -> new ForbiddenOperationException("Token has no recognised role"));
        Long userId = parseLong(jwt.getSubject());
        if (userId == null || userId <= 0) {
            throw new ForbiddenOperationException("Token subject must be a numeric user id");
        }
        return new Caller(userId, role, parseLong(jwt.getClaimAsString(TENANT_CLAIM)));
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
