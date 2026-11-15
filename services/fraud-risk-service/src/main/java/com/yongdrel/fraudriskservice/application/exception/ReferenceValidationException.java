package com.yongdrel.fraudriskservice.application.exception;

/** A referenced resource in another service is missing or inconsistent (422). */
public class ReferenceValidationException extends RuntimeException {
    public ReferenceValidationException(String message) {
        super(message);
    }
}
