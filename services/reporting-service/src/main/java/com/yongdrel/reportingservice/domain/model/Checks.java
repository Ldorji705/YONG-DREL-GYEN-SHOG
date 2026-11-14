package com.yongdrel.reportingservice.domain.model;

import com.yongdrel.reportingservice.domain.exception.InvalidProjectionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Shared field checks for projections. */
final class Checks {

    static final BigDecimal MAX_MONEY = new BigDecimal("100000000.00");

    private Checks() {
    }

    static void positive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new InvalidProjectionException(field + " must be a positive number");
        }
    }

    static void notNull(Object value, String field) {
        if (value == null) {
            throw new InvalidProjectionException(field + " is required");
        }
    }

    static void range(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new InvalidProjectionException(field + " must be between " + min + " and " + max);
        }
    }

    static void money(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_MONEY) > 0 || value.scale() > 2) {
            throw new InvalidProjectionException(field + " must be 0 - 100000000.00 with at most 2 decimals");
        }
    }

    static void text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new InvalidProjectionException(field + " must be 1-" + max + " characters");
        }
    }

    /** Same value, same representation: 100 and 100.0 both become 100.00 (what MySQL stores). */
    static BigDecimal norm(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** MySQL DATETIME(6) keeps microseconds. */
    static Instant norm(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidProjectionException(message);
        }
    }
}
