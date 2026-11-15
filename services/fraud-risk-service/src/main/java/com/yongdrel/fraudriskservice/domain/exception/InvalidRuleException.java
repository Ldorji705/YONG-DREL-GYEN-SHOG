package com.yongdrel.fraudriskservice.domain.exception;

/** A rule configuration that breaks domain constraints (mapped to 400). */
public class InvalidRuleException extends RuntimeException {
    public InvalidRuleException(String message) {
        super(message);
    }
}
