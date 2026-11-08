package com.yongdrel.transaction_ledger_service.adapter.persistence;

import com.yongdrel.transaction_ledger_service.domain.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Extends the plain Repository interface instead of JpaRepository on purpose:
 * only insert and read methods exist, so no code can delete ledger entries.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

    LedgerEntry save(LedgerEntry entry);

    Optional<LedgerEntry> findByReferenceId(String referenceId);

    Page<LedgerEntry> findByAccountIdOrderByOccurredAtDesc(String accountId, Pageable pageable);
}
