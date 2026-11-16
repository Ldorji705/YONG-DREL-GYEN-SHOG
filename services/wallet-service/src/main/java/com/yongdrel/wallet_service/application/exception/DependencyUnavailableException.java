package com.yongdrel.wallet_service.application.exception;

/** The Transaction Ledger could not be reached; the job retries later. */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message) {
        super(message);
    }
}
