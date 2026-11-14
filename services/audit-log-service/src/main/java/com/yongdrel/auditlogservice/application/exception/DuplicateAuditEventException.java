package com.yongdrel.auditlogservice.application.exception;

/** An audit event with the same eventId was already recorded (409). */
public class DuplicateAuditEventException extends RuntimeException {
    public DuplicateAuditEventException(String message) {
        super(message);
    }
}
