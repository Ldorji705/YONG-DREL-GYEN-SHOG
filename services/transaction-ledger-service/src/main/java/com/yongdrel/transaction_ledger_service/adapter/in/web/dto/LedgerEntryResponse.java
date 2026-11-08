package com.yongdrel.transaction_ledger_service.adapter.in.web.dto;

import com.yongdrel.transaction_ledger_service.domain.model.EntryDirection;
import com.yongdrel.transaction_ledger_service.domain.model.LedgerEntry;
import com.yongdrel.transaction_ledger_service.domain.model.SourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        String accountId,
        String referenceId,
        SourceType sourceType,
        EntryDirection direction,
        BigDecimal amount,
        String currency,
        String description,
        Instant occurredAt,
        Instant recordedAt
) {
    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(
                e.getId(),
                e.getAccountId(),
                e.getReferenceId(),
                e.getSourceType(),
                e.getDirection(),
                e.getAmount(),
                e.getCurrency(),
                e.getDescription(),
                e.getOccurredAt(),
                e.getRecordedAt()
        );
    }
}
