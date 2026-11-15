package com.yongdrel.auditlogservice.domain.exception;

/** An audit event that breaks the domain constraints (400 over REST, dead-lettered over RabbitMQ). */
public class InvalidAuditEventException extends RuntimeException {
    public InvalidAuditEventException(String message) {
        super(message);
    }
}
