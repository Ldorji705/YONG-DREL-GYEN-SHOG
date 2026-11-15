package com.yongdrel.auditlogservice.application.exception;

/** Requested audit event does not exist or is not visible to the caller (404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
