package com.yongdrel.tenant.application.exception;

public class DuplicateTenantException extends IllegalArgumentException {

    public DuplicateTenantException(String message) {
        super(message);
    }
}
