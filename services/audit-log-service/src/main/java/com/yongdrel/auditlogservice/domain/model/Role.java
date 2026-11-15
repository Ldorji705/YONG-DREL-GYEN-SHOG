package com.yongdrel.auditlogservice.domain.model;

import java.util.Locale;
import java.util.Optional;

/** Platform roles from the JWT "role" claim. The Auth Service's current "USER" means STUDENT. */
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
