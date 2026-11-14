package com.yongdrel.fraudriskservice.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateRiskCheckRequest(
        @NotNull @Positive Long tenantId,
        @NotNull @Positive Long eventId,
        @NotNull @Positive Long orderId,
        @NotNull @Positive Long buyerId,

        @Pattern(regexp = "^[A-Za-z0-9 -]{4,64}$",
                message = "must be 4-64 letters, digits, spaces or hyphens")
        String paymentReference,

        @Pattern(regexp = "^[A-Fa-f0-9]{64}$", message = "must be a SHA-256 hex digest (64 hex characters)")
        String proofImageHash,

        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "1000000.00") @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @DecimalMin(value = "0.01") @DecimalMax(value = "1000000.00") @Digits(integer = 10, fraction = 2)
        BigDecimal expectedAmount
) {

    @JsonIgnore
    @AssertTrue(message = "paymentReference or proofImageHash is required")
    public boolean isEvidenceProvided() {
        return (paymentReference != null && !paymentReference.isBlank())
                || (proofImageHash != null && !proofImageHash.isBlank());
    }
}
