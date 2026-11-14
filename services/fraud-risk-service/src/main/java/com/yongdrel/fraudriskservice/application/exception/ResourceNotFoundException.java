package com.yongdrel.fraudriskservice.application.exception;

/** Requested resource does not exist or is not visible to the caller (404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
