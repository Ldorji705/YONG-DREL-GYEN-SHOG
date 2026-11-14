package com.yongdrel.reportingservice.domain.exception;

/** An ingested record breaks the data constraints (400 over REST, dead-lettered over RabbitMQ). */
public class InvalidProjectionException extends RuntimeException {
    public InvalidProjectionException(String message) {
        super(message);
    }
}
