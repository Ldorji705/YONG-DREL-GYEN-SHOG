package com.yongdrel.reportingservice.application.exception;

/** The caller's role, tenant or ownership does not allow the operation (403). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
