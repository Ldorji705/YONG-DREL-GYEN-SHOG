package com.yongdrel.auditlogservice.domain.service;

import com.yongdrel.auditlogservice.domain.model.AuditEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SHA-256 over every business field of an audit event, in a fixed order.
 * Timestamps are truncated to microseconds because that is what MySQL DATETIME(6) stores.
 */
public final class IntegrityHasher {

    private IntegrityHasher() {
    }

    public static String hash(AuditEvent e) {
        String canonical = Stream.of(
                        e.eventId(), e.tenantId(), e.actorType(), e.actorId(), e.actorRole(), e.action(), e.outcome(),
                        e.targetType(), e.targetId(), e.sourceService(), e.correlationId(),
                        e.occurredAt() == null ? null : e.occurredAt().truncatedTo(ChronoUnit.MICROS),
                        e.recordedAt() == null ? null : e.recordedAt().truncatedTo(ChronoUnit.MICROS),
                        e.detailsJson())
                .map(v -> Objects.toString(v, "∅"))
                .collect(Collectors.joining("\u001F"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
