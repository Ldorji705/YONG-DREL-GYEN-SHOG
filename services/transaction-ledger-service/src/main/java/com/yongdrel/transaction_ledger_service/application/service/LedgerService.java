package com.yongdrel.transaction_ledger_service.application.service;

import com.yongdrel.transaction_ledger_service.adapter.in.web.dto.CreateLedgerEntryRequest;
import com.yongdrel.transaction_ledger_service.adapter.persistence.LedgerEntryRepository;
import com.yongdrel.transaction_ledger_service.application.exception.LedgerReferenceConflictException;
import com.yongdrel.transaction_ledger_service.domain.model.LedgerEntry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LedgerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final LedgerEntryRepository repository;

    public LedgerService(LedgerEntryRepository repository) {
        this.repository = repository;
    }

    /** Result of recording: the entry plus whether it was newly created. */
    public record RecordResult(LedgerEntry entry, boolean created) {
    }

    /**
     * Idempotent append: the same referenceId is only ever stored once.
     * A retry with identical details returns the existing entry (created = false).
     * A retry with different details is rejected as a conflict.
     */
    public RecordResult record(CreateLedgerEntryRequest request) {

        // 1. Fast path: already recorded?
        var existing = repository.findByReferenceId(request.referenceId());
        if (existing.isPresent()) {
            return new RecordResult(ensureSameDetails(existing.get(), request), false);
        }

        // 2. Try to insert. If two requests race, the UNIQUE(reference_id)
        //    constraint in MySQL lets only one win; the loser lands in the catch.
        LedgerEntry entry = new LedgerEntry(
                request.accountId(),
                request.referenceId(),
                request.sourceType(),
                request.direction(),
                request.amount(),
                request.currency(),
                request.description(),
                request.occurredAt() != null ? request.occurredAt() : Instant.now()
        );

        try {
            return new RecordResult(repository.save(entry), true);
        } catch (DataIntegrityViolationException raceLost) {
            LedgerEntry winner = repository.findByReferenceId(request.referenceId())
                    .orElseThrow(() -> raceLost);
            return new RecordResult(ensureSameDetails(winner, request), false);
        }
    }

    /** History for one account, newest first. */
    public Page<LedgerEntry> findForAccount(String accountId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return repository.findByAccountIdOrderByOccurredAtDesc(
                accountId, PageRequest.of(safePage, safeSize));
    }

    private LedgerEntry ensureSameDetails(LedgerEntry existing, CreateLedgerEntryRequest request) {
        boolean same =
                existing.getAccountId().equals(request.accountId())
                && existing.getSourceType() == request.sourceType()
                && existing.getDirection() == request.direction()
                && existing.getAmount().compareTo(request.amount()) == 0
                && existing.getCurrency().equals(request.currency());

        if (!same) {
            throw new LedgerReferenceConflictException(request.referenceId());
        }
        return existing;
    }
}
