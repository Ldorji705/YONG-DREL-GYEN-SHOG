package com.yongdrel.refund_service.adapter.out.persistence;

import com.yongdrel.refund_service.domain.model.PaymentRefundBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundBalanceRepository extends Repository<PaymentRefundBalance, UUID> {

    PaymentRefundBalance save(PaymentRefundBalance balance);

    /**
     * Creates the balance row only if it does not exist yet, in one atomic statement.
     * Safe when many first refunds for the same payment arrive at once (no deadlock, no duplicate).
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO payment_refund_balances
                (payment_id, paid_amount, reserved_amount, currency, updated_at)
            VALUES (:paymentId, :paidAmount, 0, :currency, UTC_TIMESTAMP(6))
            """, nativeQuery = true)
    int insertIfAbsent(@Param("paymentId") byte[] paymentId,
                       @Param("paidAmount") BigDecimal paidAmount,
                       @Param("currency") String currency);

    /** SELECT ... FOR UPDATE: other transactions wait until this one commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PaymentRefundBalance b where b.paymentId = :paymentId")
    Optional<PaymentRefundBalance> findForUpdate(@Param("paymentId") UUID paymentId);
}
