package com.yongdrel.reportingservice.application.exception;

/** Requested event or saved report does not exist or is not visible to the caller (404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
