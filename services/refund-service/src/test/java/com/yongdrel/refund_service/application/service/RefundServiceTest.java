package com.yongdrel.refund_service.application.service;

import com.yongdrel.refund_service.adapter.in.web.dto.CreateRefundRequest;
import com.yongdrel.refund_service.adapter.out.persistence.PaymentRefundBalanceRepository;
import com.yongdrel.refund_service.adapter.out.persistence.RefundRepository;
import com.yongdrel.refund_service.application.exception.DependencyUnavailableException;
import com.yongdrel.refund_service.application.exception.IdempotencyKeyConflictException;
import com.yongdrel.refund_service.application.exception.InvalidRefundStateException;
import com.yongdrel.refund_service.application.exception.OverRefundException;
import com.yongdrel.refund_service.application.exception.PaymentNotFoundException;
import com.yongdrel.refund_service.application.exception.RefundNotAllowedException;
import com.yongdrel.refund_service.application.exception.RefundNotFoundException;
import com.yongdrel.refund_service.application.port.LedgerPort;
import com.yongdrel.refund_service.application.port.PaymentLookupPort;
import com.yongdrel.refund_service.application.port.PaymentSnapshot;
import com.yongdrel.refund_service.application.port.RefundProviderPort;
import com.yongdrel.refund_service.domain.model.PaymentRefundBalance;
import com.yongdrel.refund_service.domain.model.Refund;
import com.yongdrel.refund_service.domain.model.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit tests: every collaborator is a mock, no database or HTTP. */
class RefundServiceTest {

    private static final String USER = "user-a";
    private static final String KEY = "key-1";

    private RefundRepository refunds;
    private PaymentRefundBalanceRepository balances;
    private PaymentLookupPort payments;
    private RefundProviderPort provider;
    private LedgerPort ledger;
    private RefundService service;

    private final UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        refunds = mock(RefundRepository.class);
        balances = mock(PaymentRefundBalanceRepository.class);
        payments = mock(PaymentLookupPort.class);
        provider = mock(RefundProviderPort.class);
        ledger = mock(LedgerPort.class);
        // Real TransactionTemplate over a mock manager: callbacks run, nothing touches a DB.
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new RefundService(refunds, balances, payments, provider, ledger, tx);

        when(refunds.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(balances.save(any(PaymentRefundBalance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(provider.refund(any(), any(), anyString())).thenReturn("MOCK-REF");
    }

    private void paymentIs(String owner, String status, String paid) {
        when(payments.getPayment(any(), anyString())).thenReturn(new PaymentSnapshot(
                paymentId, UUID.randomUUID(), owner, new BigDecimal(paid), "BTN", status));
    }

    private PaymentRefundBalance balance(String paid, String alreadyReserved) {
        PaymentRefundBalance b = new PaymentRefundBalance(paymentId, new BigDecimal(paid), "BTN");
        b.tryReserve(new BigDecimal(alreadyReserved));
        when(balances.findForUpdate(paymentId)).thenReturn(Optional.of(b));
        return b;
    }

    private CreateRefundRequest req(String amount) {
        return new CreateRefundRequest(paymentId, new BigDecimal(amount), "test");
    }

    @Test
    void newRefund_isCompleted_andRecordedInLedger() {
        paymentIs(USER, "CONFIRMED", "100.00");
        PaymentRefundBalance b = balance("100.00", "0");

        RefundService.CreateResult r = service.create(req("40.00"), USER, "jwt", KEY);

        assertThat(r.created()).isTrue();
        assertThat(r.refund().getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(r.refund().getProviderReference()).isEqualTo("MOCK-REF");
        assertThat(r.refund().getAttempts()).isEqualTo(1);
        assertThat(b.getReservedAmount()).isEqualByComparingTo("40");
        verify(ledger).recordRefund(r.refund());
    }

    @Test
    void sameKeySameRequest_returnsExisting_withoutCallingPayment() {
        Refund existing = new Refund(paymentId, UUID.randomUUID(), USER, new BigDecimal("40.0000"), "BTN", null, KEY);
        when(refunds.findByUserIdAndIdempotencyKey(USER, KEY)).thenReturn(Optional.of(existing));

        RefundService.CreateResult r = service.create(req("40.00"), USER, "jwt", KEY);

        assertThat(r.created()).isFalse();
        assertThat(r.refund()).isSameAs(existing);
        verify(payments, never()).getPayment(any(), anyString());
        verify(provider, never()).refund(any(), any(), anyString());
    }

    @Test
    void sameKeyDifferentAmount_isConflict() {
        Refund existing = new Refund(paymentId, UUID.randomUUID(), USER, new BigDecimal("40"), "BTN", null, KEY);
        when(refunds.findByUserIdAndIdempotencyKey(USER, KEY)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(req("70.00"), USER, "jwt", KEY))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void someoneElsesPayment_isNotFound() {
        paymentIs("user-b", "CONFIRMED", "100.00");

        assertThatThrownBy(() -> service.create(req("10"), USER, "jwt", KEY))
                .isInstanceOf(PaymentNotFoundException.class);
        verify(refunds, never()).save(any());
    }

    @Test
    void unconfirmedPayment_isNotRefundable() {
        paymentIs(USER, "PENDING", "100.00");

        assertThatThrownBy(() -> service.create(req("10"), USER, "jwt", KEY))
                .isInstanceOf(RefundNotAllowedException.class);
        verify(refunds, never()).save(any());
    }

    @Test
    void moreThanRemaining_isOverRefund_andNothingSaved() {
        paymentIs(USER, "CONFIRMED", "100.00");
        PaymentRefundBalance b = balance("100.00", "90.00");

        assertThatThrownBy(() -> service.create(req("20.00"), USER, "jwt", KEY))
                .isInstanceOf(OverRefundException.class);
        assertThat(b.getReservedAmount()).isEqualByComparingTo("90");
        verify(refunds, never()).save(any());
    }

    @Test
    void exactlyTheRemainingAmount_isAllowed() {
        paymentIs(USER, "CONFIRMED", "100.00");
        PaymentRefundBalance b = balance("100.00", "60.00");

        service.create(req("40.00"), USER, "jwt", KEY);

        assertThat(b.remaining()).isEqualByComparingTo("0");
    }

    @Test
    void providerFailure_keepsRefundAsFailed_andSkipsLedger() {
        paymentIs(USER, "CONFIRMED", "100.00");
        balance("100.00", "0");
        when(provider.refund(any(), any(), anyString()))
                .thenThrow(new DependencyUnavailableException("provider down"));

        Refund r = service.create(req("10"), USER, "jwt", KEY).refund();

        assertThat(r.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(r.getFailureReason()).contains("provider down");
        verify(ledger, never()).recordRefund(any());
    }

    @Test
    void ledgerFailure_keepsRefundAsFailed() {
        paymentIs(USER, "CONFIRMED", "100.00");
        balance("100.00", "0");
        doThrow(new DependencyUnavailableException("ledger down")).when(ledger).recordRefund(any());

        Refund r = service.create(req("10"), USER, "jwt", KEY).refund();

        assertThat(r.getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void retryOfFailedRefund_completesIt() {
        Refund failed = new Refund(paymentId, UUID.randomUUID(), USER, new BigDecimal("10"), "BTN", null, KEY);
        failed.recordAttempt();
        failed.markFailed("provider down");
        when(refunds.findById(failed.getId())).thenReturn(Optional.of(failed));

        Refund r = service.retry(failed.getId(), USER, false);

        assertThat(r.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(r.getAttempts()).isEqualTo(2);
        assertThat(r.getFailureReason()).isNull();
    }

    @Test
    void retryOfCompletedRefund_isRejected() {
        Refund done = new Refund(paymentId, UUID.randomUUID(), USER, new BigDecimal("10"), "BTN", null, KEY);
        done.markCompleted("REF");
        when(refunds.findById(done.getId())).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.retry(done.getId(), USER, false))
                .isInstanceOf(InvalidRefundStateException.class);
    }

    @Test
    void otherUser_cannotSeeRefund_butAdminCan() {
        Refund r = new Refund(paymentId, UUID.randomUUID(), USER, new BigDecimal("10"), "BTN", null, KEY);
        when(refunds.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.get(r.getId(), "user-b", false))
                .isInstanceOf(RefundNotFoundException.class);
        assertThat(service.get(r.getId(), "admin-x", true)).isSameAs(r);
    }
}
