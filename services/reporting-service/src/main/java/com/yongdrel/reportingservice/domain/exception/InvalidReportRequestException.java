package com.yongdrel.reportingservice.domain.exception;

/** Report filters or a saved report definition are invalid (400). */
public class InvalidReportRequestException extends RuntimeException {
    public InvalidReportRequestException(String message) {
        super(message);
    }
}
