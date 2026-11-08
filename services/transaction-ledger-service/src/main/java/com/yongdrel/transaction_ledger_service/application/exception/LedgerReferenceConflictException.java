package com.yongdrel.transaction_ledger_service.application.exception;

/**
 * Thrown when a referenceId is already recorded but the new request
 * carries different details (amount, account, direction, ...).
 * That means two different transactions are using the same reference.
 */
public class LedgerReferenceConflictException extends RuntimeException {

    public LedgerReferenceConflictException(String referenceId) {
        super("Reference '" + referenceId + "' is already recorded with different details");
    }
}
