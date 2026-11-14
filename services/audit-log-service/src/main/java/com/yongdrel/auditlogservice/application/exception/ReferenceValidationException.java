package com.yongdrel.auditlogservice.application.exception;

/** A cross-service reference (tenant, source service) is unknown (422). */
public class ReferenceValidationException extends RuntimeException {
    public ReferenceValidationException(String message) {
        super(message);
    }
}
