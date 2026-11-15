package com.yongdrel.reportingservice.domain.exception;

/** An ingested record is older than, or conflicts with, the stored copy (409). */
public class StaleUpdateException extends RuntimeException {
    public StaleUpdateException(String message) {
        super(message);
    }
}
