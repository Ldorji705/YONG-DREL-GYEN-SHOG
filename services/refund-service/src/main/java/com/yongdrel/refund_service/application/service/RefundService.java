package com.yongdrel.refund_service.application.service;

import com.yongdrel.refund_service.adapter.in.web.dto.CreateRefundRequest;
import com.yongdrel.refund_service.adapter.out.persistence.PaymentRefundBalanceRepository;
import com.yongdrel.refund_service.adapter.out.persistence.RefundRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refunds;
    private final PaymentRefundBalanceRepository balances;
    private final PaymentLookupPort payments;
    private final RefundProviderPort provider;
    private final LedgerPort ledger;
    private final TransactionTemplate tx;

    public RefundService(RefundRepository refunds, PaymentRefundBalanceRepository balances,
                         PaymentLookupPort payments, RefundProviderPort provider,
                         LedgerPort ledger, TransactionTemplate tx) {
        this.refunds = refunds;
        this.balances = balances;
        this.payments = payments;
        this.provider = provider;
        this.ledger = ledger;
        this.tx = tx;
    }

    public record CreateResult(Refund refund, boolean created) {
    }

    /**
     * 1. Same user + Idempotency-Key seen before: return that refund (safe retry).
     * 2. Check the payment with the Payment Service (must be CONFIRMED and the caller's).
     * 3. Lock the payment's balance row and reserve the amount (no over-refund, even concurrently).
     * 4. Call the provider and record the ledger entry; failures are kept as FAILED for retry.
     */
    public CreateResult create(CreateRefundRequest request, String userId,
                               String bearerToken, String idempotencyKey) {

        Optional<Refund> previous = refunds.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            return new CreateResult(ensureSameRequest(previous.get(), request), false);
        }

        PaymentSnapshot payment = payments.getPayment(request.paymentId(), bearerToken);
        if (payment.userId() == null || !payment.userId().equals(userId)) {
            throw new PaymentNotFoundException("Payment " + request.paymentId() + " not found");
        }
        if (!payment.isConfirmed()) {
            throw new RefundNotAllowedException(
                    "Payment is " + payment.status() + "; only CONFIRMED payments can be refunded");
        }

        ensureBalanceRow(payment);

        Refund refund;
        try {
            refund = tx.execute(status -> reserveAndCreate(request, payment, userId, idempotencyKey));
        } catch (DataIntegrityViolationException raceLost) {
            // Another request with the same Idempotency-Key committed first.
            Refund winner = refunds.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> raceLost);
            return new CreateResult(ensureSameRequest(winner, request), false);
        }

        return new CreateResult(process(refund), true);
    }

    /** Re-runs provider + ledger for a FAILED refund. The amount stays reserved meanwhile. */
    public Refund retry(UUID refundId, String userId, boolean isAdmin) {
        Refund refund = get(refundId, userId, isAdmin);
        if (refund.getStatus() != RefundStatus.FAILED) {
            throw new InvalidRefundStateException(
                    "Only FAILED refunds can be retried; this one is " + refund.getStatus());
        }
        return process(refund);
    }

    /** Owner or ADMIN only; anyone else gets 404 so refund IDs cannot be probed. */
    public Refund get(UUID refundId, String userId, boolean isAdmin) {
        Refund refund = refunds.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund " + refundId + " not found"));
        if (!isAdmin && !refund.getUserId().equals(userId)) {
            throw new RefundNotFoundException("Refund " + refundId + " not found");
        }
        return refund;
    }

    // ------------------------------------------------------------------

    private Refund reserveAndCreate(CreateRefundRequest request, PaymentSnapshot payment,
                                    String userId, String idempotencyKey) {
        PaymentRefundBalance balance = balances.findForUpdate(payment.id())
                .orElseThrow(() -> new IllegalStateException("Balance row missing for " + payment.id()));

        if (!balance.tryReserve(request.amount())) {
            throw new OverRefundException("Requested " + request.amount() + " but only "
                    + balance.remaining() + " " + balance.getCurrency() + " is refundable");
        }
        balances.save(balance);

        return refunds.save(new Refund(payment.id(), payment.orderId(), userId,
                request.amount(), payment.currency(), request.reason(), idempotencyKey));
    }

    /** Creates the payment's balance row once (atomic INSERT IGNORE, safe under concurrency). */
    private void ensureBalanceRow(PaymentSnapshot payment) {
        tx.executeWithoutResult(status ->
                balances.insertIfAbsent(toBytes(payment.id()), payment.amount(), payment.currency()));
    }

    private static byte[] toBytes(UUID id) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private Refund process(Refund refund) {
        refund.recordAttempt();
        try {
            String providerRef = provider.refund(refund.getId(), refund.getAmount(), refund.getCurrency());
            ledger.recordRefund(refund);
            refund.markCompleted(providerRef);
            log.info("Refund {} completed (attempt {})", refund.getId(), refund.getAttempts());
        } catch (RuntimeException e) {
            refund.markFailed(e.getMessage());
            log.warn("Refund {} failed (attempt {}): {}", refund.getId(), refund.getAttempts(), e.getMessage());
        }
        return refunds.save(refund);
    }

    private Refund ensureSameRequest(Refund existing, CreateRefundRequest request) {
        boolean same = existing.getPaymentId().equals(request.paymentId())
                && existing.getAmount().compareTo(request.amount()) == 0;
        if (!same) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used for a different refund request");
        }
        return existing;
    }
}
