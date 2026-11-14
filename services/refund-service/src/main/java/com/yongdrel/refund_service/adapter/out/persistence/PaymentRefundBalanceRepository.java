package com.yongdrel.refund_service.adapter.out.persistence;

import com.yongdrel.refund_service.domain.model.PaymentRefundBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundBalanceRepository extends Repository<PaymentRefundBalance, UUID> {

    PaymentRefundBalance save(PaymentRefundBalance balance);

    /** SELECT ... FOR UPDATE: other transactions wait until this one commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PaymentRefundBalance b where b.paymentId = :paymentId")
    Optional<PaymentRefundBalance> findForUpdate(@Param("paymentId") UUID paymentId);
}
