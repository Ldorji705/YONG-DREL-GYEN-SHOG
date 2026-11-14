package com.yongdrel.refund_service.application.port;

import com.yongdrel.refund_service.domain.model.Refund;

public interface LedgerPort {

    /** Records the refund as a CREDIT in the Transaction Ledger. Idempotent per refund. */
    void recordRefund(Refund refund);
}
