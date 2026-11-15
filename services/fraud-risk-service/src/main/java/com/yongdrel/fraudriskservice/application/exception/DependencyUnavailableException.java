package com.yongdrel.fraudriskservice.application.exception;

/** A downstream service could not be reached (503). */
public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message) {
        super(message);
    }
}
