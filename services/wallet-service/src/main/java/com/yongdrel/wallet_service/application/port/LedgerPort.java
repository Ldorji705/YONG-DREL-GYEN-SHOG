package com.yongdrel.wallet_service.application.port;

import com.yongdrel.wallet_service.domain.model.WalletOperation;

public interface LedgerPort {

    /** Records the operation in the Transaction Ledger. Idempotent per operation ID. */
    void recordOperation(WalletOperation operation, String ownerId, String currency);
}
