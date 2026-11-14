package com.yongdrel.reportingservice.application.exception;

/** A saved report name is taken, or a concurrent update happened (409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
