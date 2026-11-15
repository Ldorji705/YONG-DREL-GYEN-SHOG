package com.yongdrel.fraudriskservice.application.service;

import com.yongdrel.fraudriskservice.domain.model.TransactionDetails;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Re-submitting the exact same proof for the same order returns the original check (repeat-safe). */
final class IdempotencyKey {

    private IdempotencyKey() {
    }

    static String of(TransactionDetails tx) {
        String raw = String.join("|",
                String.valueOf(tx.tenantId()),
                String.valueOf(tx.orderId()),
                String.valueOf(tx.buyerId()),
                String.valueOf(tx.paymentReference()),
                String.valueOf(tx.proofImageHash()),
                tx.amount().stripTrailingZeros().toPlainString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
