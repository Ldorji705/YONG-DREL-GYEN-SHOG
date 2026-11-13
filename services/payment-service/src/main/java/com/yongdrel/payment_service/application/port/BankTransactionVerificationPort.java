package com.yongdrel.payment_service.application.port;

public interface BankTransactionVerificationPort {

    boolean verify(BankTransactionVerificationRequest request);
}
