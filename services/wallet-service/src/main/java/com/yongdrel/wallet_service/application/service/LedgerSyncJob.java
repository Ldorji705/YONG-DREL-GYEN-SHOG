package com.yongdrel.wallet_service.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every few seconds, re-sends wallet operations the Ledger did not accept yet. */
@Component
@ConditionalOnProperty(name = "app.wallet.ledger-sync.enabled", havingValue = "true")
public class LedgerSyncJob {

    private final WalletService walletService;

    public LedgerSyncJob(WalletService walletService) {
        this.walletService = walletService;
    }

    @Scheduled(fixedDelayString = "${app.wallet.ledger-sync.delay-ms:30000}")
    public void run() {
        walletService.syncPendingToLedger(50);
    }
}
