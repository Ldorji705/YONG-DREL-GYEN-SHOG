package com.yongdrel.refund_service.application.exception;

/** Payment Service, provider or Ledger could not be reached (503 / kept as FAILED). */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message) {
        super(message);
    }
}
