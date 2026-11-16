package com.yongdrel.wallet_service.adapter.out.persistence;

import com.yongdrel.wallet_service.domain.model.WalletOperation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Plain Repository (no delete methods): operations are never deleted. */
public interface WalletOperationRepository extends Repository<WalletOperation, UUID> {

    WalletOperation save(WalletOperation operation);

    Optional<WalletOperation> findByWalletIdAndRequestKey(UUID walletId, String requestKey);

    /** Operations not yet accepted by the Ledger, oldest first (for the retry job). */
    List<WalletOperation> findByLedgerRecordedFalseOrderByCreatedAtAsc(Pageable pageable);
}
