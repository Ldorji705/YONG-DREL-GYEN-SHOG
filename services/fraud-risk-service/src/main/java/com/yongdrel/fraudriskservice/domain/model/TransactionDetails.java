package com.yongdrel.fraudriskservice.domain.model;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** A payment submission to be risk-checked, with identifiers already normalized. */
public record TransactionDetails(
        Long tenantId,
        Long eventId,
        Long orderId,
        Long buyerId,
        String paymentReference,
        String proofImageHash,
        BigDecimal amount,
        BigDecimal expectedAmount
) {

    public TransactionDetails {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(amount, "amount");
        paymentReference = normalizeReference(paymentReference);
        proofImageHash = normalizeHash(proofImageHash);
    }

    /** "txn 12-34 ab" and "TXN1234AB" are the same bank reference. */
    public static String normalizeReference(String reference) {
        if (reference == null) {
            return null;
        }
        String normalized = reference.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public static String normalizeHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        return hash.trim().toLowerCase(Locale.ROOT);
    }
}
