package com.yongdrel.reportingservice.application.exception;

/** An ingested record references an unknown or foreign event/prize (422). */
public class ReferenceValidationException extends RuntimeException {
    public ReferenceValidationException(String message) {
        super(message);
    }
}
