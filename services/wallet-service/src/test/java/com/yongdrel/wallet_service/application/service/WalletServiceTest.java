package com.yongdrel.wallet_service.application.service;

import com.yongdrel.wallet_service.adapter.out.persistence.WalletOperationRepository;
import com.yongdrel.wallet_service.adapter.out.persistence.WalletRepository;
import com.yongdrel.wallet_service.application.exception.DependencyUnavailableException;
import com.yongdrel.wallet_service.application.exception.IdempotencyKeyConflictException;
import com.yongdrel.wallet_service.application.exception.InsufficientFundsException;
import com.yongdrel.wallet_service.application.port.LedgerPort;
import com.yongdrel.wallet_service.domain.model.OperationType;
import com.yongdrel.wallet_service.domain.model.Wallet;
import com.yongdrel.wallet_service.domain.model.WalletOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit tests: repositories and Ledger are mocks. */
class WalletServiceTest {

    private static final String USER = "user-a";

    private WalletRepository wallets;
    private WalletOperationRepository operations;
    private LedgerPort ledger;
    private WalletService service;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallets = mock(WalletRepository.class);
        operations = mock(WalletOperationRepository.class);
        ledger = mock(LedgerPort.class);
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new WalletService(wallets, operations, ledger, tx, "BTN");

        wallet = new Wallet(USER, "BTN");
        when(wallets.findByOwnerIdForUpdate(USER)).thenReturn(Optional.of(wallet));
        when(wallets.findByOwnerId(USER)).thenReturn(Optional.of(wallet));
        when(wallets.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(wallets.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operations.save(any(WalletOperation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void balanceIs(String amount) {
        wallet.credit(new BigDecimal(amount));
    }

    @Test
    void credit_increasesBalance_andIsRecordedInLedger() {
        WalletService.ApplyResult r = service.credit(USER, "k1", new BigDecimal("100.00"), "top-up");

        assertThat(r.created()).isTrue();
        assertThat(r.operation().getType()).isEqualTo(OperationType.CREDIT);
        assertThat(r.operation().getBalanceAfter()).isEqualByComparingTo("100");
        assertThat(r.operation().isLedgerRecorded()).isTrue();
        verify(ledger).recordOperation(r.operation(), USER, "BTN");
    }

    @Test
    void debit_decreasesBalance() {
        balanceIs("100");

        WalletService.ApplyResult r = service.debit(USER, "k1", new BigDecimal("30"), "ticket");

        assertThat(r.operation().getBalanceAfter()).isEqualByComparingTo("70");
        assertThat(wallet.getBalance()).isEqualByComparingTo("70");
    }

    @Test
    void debit_exactBalance_reachesZero() {
        balanceIs("50");

        service.debit(USER, "k1", new BigDecimal("50"), null);

        assertThat(wallet.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void debit_moreThanBalance_isRefused_andNothingSaved() {
        balanceIs("20");

        assertThatThrownBy(() -> service.debit(USER, "k1", new BigDecimal("20.0001"), null))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("20");
        verify(operations, never()).save(any());
        verify(ledger, never()).recordOperation(any(), anyString(), anyString());
    }

    @Test
    void sameKeySameRequest_returnsExisting_withoutChangingBalance() {
        balanceIs("100");
        WalletOperation earlier = new WalletOperation(wallet.getId(), "k1", OperationType.DEBIT,
                new BigDecimal("30"), new BigDecimal("70"), null);
        when(operations.findByWalletIdAndRequestKey(wallet.getId(), "k1")).thenReturn(Optional.of(earlier));

        WalletService.ApplyResult r = service.debit(USER, "k1", new BigDecimal("30.00"), null);

        assertThat(r.created()).isFalse();
        assertThat(r.operation()).isSameAs(earlier);
        assertThat(wallet.getBalance()).isEqualByComparingTo("100");
        verify(ledger, never()).recordOperation(any(), anyString(), anyString());
    }

    @Test
    void sameKeyDifferentAmount_isConflict() {
        WalletOperation earlier = new WalletOperation(wallet.getId(), "k1", OperationType.DEBIT,
                new BigDecimal("30"), new BigDecimal("70"), null);
        when(operations.findByWalletIdAndRequestKey(wallet.getId(), "k1")).thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> service.debit(USER, "k1", new BigDecimal("31"), null))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void sameKeyDifferentType_isConflict() {
        WalletOperation earlier = new WalletOperation(wallet.getId(), "k1", OperationType.DEBIT,
                new BigDecimal("30"), new BigDecimal("70"), null);
        when(operations.findByWalletIdAndRequestKey(wallet.getId(), "k1")).thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> service.credit(USER, "k1", new BigDecimal("30"), null))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void ledgerDown_operationStillApplied_butMarkedNotRecorded() {
        doThrow(new DependencyUnavailableException("down")).when(ledger).recordOperation(any(), anyString(), anyString());

        WalletService.ApplyResult r = service.credit(USER, "k1", new BigDecimal("10"), null);

        assertThat(r.created()).isTrue();
        assertThat(wallet.getBalance()).isEqualByComparingTo("10");
        assertThat(r.operation().isLedgerRecorded()).isFalse();
    }

    @Test
    void syncJob_resendsPendingOperations() {
        WalletOperation pending = new WalletOperation(wallet.getId(), "k9", OperationType.CREDIT,
                new BigDecimal("5"), new BigDecimal("5"), null);
        when(operations.findByLedgerRecordedFalseOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(pending));

        int sent = service.syncPendingToLedger(50);

        assertThat(sent).isEqualTo(1);
        assertThat(pending.isLedgerRecorded()).isTrue();
        verify(ledger).recordOperation(eq(pending), eq(USER), eq("BTN"));
    }
}
