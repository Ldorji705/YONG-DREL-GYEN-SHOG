package com.yongdrel.transaction_ledger_service.adapter.in.web.dto;

import com.yongdrel.transaction_ledger_service.domain.model.EntryDirection;
import com.yongdrel.transaction_ledger_service.domain.model.SourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Body of POST /api/ledger/entries (internal services only). */
public record CreateLedgerEntryRequest(

        @NotBlank @Size(max = 100)
        String accountId,

        @NotBlank @Size(max = 100)
        String referenceId,

        @NotNull
        SourceType sourceType,

        @NotNull
        EntryDirection direction,

        @NotNull
        @DecimalMin(value = "0.0001")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter uppercase currency code")
        String currency,

        @Size(max = 255)
        String description,

        /** Optional; defaults to the time the ledger records it. */
        Instant occurredAt
) {
}
