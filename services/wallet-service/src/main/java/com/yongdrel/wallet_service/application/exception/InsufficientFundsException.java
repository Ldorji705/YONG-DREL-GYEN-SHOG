package com.yongdrel.wallet_service.application.exception;

/** Debit would make the balance negative (422). */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
