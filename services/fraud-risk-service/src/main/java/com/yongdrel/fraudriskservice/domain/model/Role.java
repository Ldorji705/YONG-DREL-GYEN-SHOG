package com.yongdrel.fraudriskservice.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Platform roles as carried in the JWT "role" claim.
 * The Auth Service currently issues "USER" for students, so it maps to STUDENT.
 * SERVICE is for service-to-service calls (e.g. Payment Proof Service).
 */
public enum Role {
    STUDENT, ORGANIZER, ADMIN, SERVICE;

    public static Optional<Role> fromClaim(String claim) {
        if (claim == null || claim.isBlank()) {
            return Optional.empty();
        }
        String value = claim.trim().toUpperCase(Locale.ROOT);
        if (value.startsWith("ROLE_")) {
            value = value.substring(5);
        }
        if (value.equals("USER")) {
            return Optional.of(STUDENT);
        }
        try {
            return Optional.of(Role.valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
