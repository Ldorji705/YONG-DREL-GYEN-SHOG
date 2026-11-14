package com.yongdrel.fraudriskservice.application.exception;

/** The request conflicts with existing state, e.g. a duplicate rule (409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
