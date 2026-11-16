package com.yongdrel.wallet_service.application.service;

import com.yongdrel.wallet_service.adapter.out.persistence.WalletOperationRepository;
import com.yongdrel.wallet_service.adapter.out.persistence.WalletRepository;
import com.yongdrel.wallet_service.application.exception.IdempotencyKeyConflictException;
import com.yongdrel.wallet_service.application.exception.InsufficientFundsException;
import com.yongdrel.wallet_service.application.port.LedgerPort;
import com.yongdrel.wallet_service.domain.model.OperationType;
import com.yongdrel.wallet_service.domain.model.Wallet;
import com.yongdrel.wallet_service.domain.model.WalletOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository wallets;
    private final WalletOperationRepository operations;
    private final LedgerPort ledger;
    private final TransactionTemplate tx;
    private final String currency;

    public WalletService(WalletRepository wallets, WalletOperationRepository operations,
                         LedgerPort ledger, TransactionTemplate tx,
                         @Value("${app.wallet.currency:BTN}") String currency) {
        this.wallets = wallets;
        this.operations = operations;
        this.ledger = ledger;
        this.tx = tx;
        this.currency = currency;
    }

    /** Result of a credit/debit: the operation and whether it was newly applied. */
    public record ApplyResult(WalletOperation operation, boolean created) {
    }

    /** The user's wallet; created with balance 0 on first use. */
    public Wallet getOrCreate(String ownerId) {
        ensureWallet(ownerId);
        return wallets.findByOwnerId(ownerId)
                .orElseThrow(() -> new IllegalStateException("Wallet missing for " + ownerId));
    }

    public ApplyResult debit(String ownerId, String requestKey, BigDecimal amount, String description) {
        return apply(ownerId, requestKey, OperationType.DEBIT, amount, description);
    }

    public ApplyResult credit(String ownerId, String requestKey, BigDecimal amount, String description) {
        return apply(ownerId, requestKey, OperationType.CREDIT, amount, description);
    }

    /**
     * 1. Lock the wallet row (SELECT ... FOR UPDATE): operations on one wallet run one at a time.
     * 2. Same request key seen before: return that operation (applied only once).
     * 3. Apply the change; a debit that would go below 0 is refused.
     * 4. After commit, record in the Ledger. If the Ledger is down, the background job retries.
     */
    private ApplyResult apply(String ownerId, String requestKey, OperationType type,
                              BigDecimal amount, String description) {
        ensureWallet(ownerId);

        ApplyResult result;
        try {
            result = tx.execute(status -> applyLocked(ownerId, requestKey, type, amount, description));
        } catch (DataIntegrityViolationException raceLost) {
            // Extremely unlikely with the row lock, but the UNIQUE key is the final guard.
            Wallet w = wallets.findByOwnerId(ownerId).orElseThrow(() -> raceLost);
            WalletOperation existing = operations.findByWalletIdAndRequestKey(w.getId(), requestKey)
                    .orElseThrow(() -> raceLost);
            return new ApplyResult(ensureSame(existing, type, amount), false);
        }

        if (result.created()) {
            sendToLedger(result.operation(), ownerId);
        }
        return result;
    }

    private ApplyResult applyLocked(String ownerId, String requestKey, OperationType type,
                                    BigDecimal amount, String description) {
        Wallet wallet = wallets.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new IllegalStateException("Wallet missing for " + ownerId));

        Optional<WalletOperation> previous = operations.findByWalletIdAndRequestKey(wallet.getId(), requestKey);
        if (previous.isPresent()) {
            return new ApplyResult(ensureSame(previous.get(), type, amount), false);
        }

        if (type == OperationType.DEBIT) {
            if (!wallet.tryDebit(amount)) {
                throw new InsufficientFundsException("Balance " + wallet.getBalance() + " "
                        + wallet.getCurrency() + " is less than " + amount);
            }
        } else {
            wallet.credit(amount);
        }
        wallets.save(wallet);

        WalletOperation op = operations.save(new WalletOperation(
                wallet.getId(), requestKey, type, amount, wallet.getBalance(), description));
        return new ApplyResult(op, true);
    }

    /** Re-sends operations the Ledger has not accepted yet. Called by LedgerSyncJob. */
    public int syncPendingToLedger(int batchSize) {
        List<WalletOperation> pending = operations.findByLedgerRecordedFalseOrderByCreatedAtAsc(
                PageRequest.of(0, batchSize));
        int sent = 0;
        for (WalletOperation op : pending) {
            String owner = wallets.findById(op.getWalletId()).map(Wallet::getOwnerId).orElse(null);
            if (owner != null && sendToLedger(op, owner)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendToLedger(WalletOperation op, String ownerId) {
        try {
            ledger.recordOperation(op, ownerId, currency);
            op.markLedgerRecorded();
            operations.save(op);
            return true;
        } catch (RuntimeException e) {
            log.warn("Wallet op {} not yet in Ledger, will retry: {}", op.getId(), e.getMessage());
            return false;
        }
    }

    private void ensureWallet(String ownerId) {
        tx.executeWithoutResult(status ->
                wallets.insertIfAbsent(toBytes(UUID.randomUUID()), ownerId, currency));
    }

    private static WalletOperation ensureSame(WalletOperation existing, OperationType type, BigDecimal amount) {
        if (existing.getType() != type || existing.getAmount().compareTo(amount) != 0) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used for a different wallet operation");
        }
        return existing;
    }

    private static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
