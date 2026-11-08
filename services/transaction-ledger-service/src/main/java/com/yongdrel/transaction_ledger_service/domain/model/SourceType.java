package com.yongdrel.transaction_ledger_service.domain.model;

/** Which service the financial movement came from. */
public enum SourceType {
    WALLET,
    PAYMENT,
    REFUND
}
