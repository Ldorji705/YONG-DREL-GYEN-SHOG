package com.yongdrel.transaction_ledger_service.adapter.in.web.exception;

import com.yongdrel.transaction_ledger_service.application.exception.LedgerReferenceConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Clear error codes without leaking stack traces (handbook section 9). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LedgerReferenceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(LedgerReferenceConflictException ex) {
        return body(HttpStatus.CONFLICT, "DUPLICATE_REFERENCE_CONFLICT", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fields.putIfAbsent(err.getField(), err.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or has invalid values", null);
    }

    private ResponseEntity<Map<String, Object>> body(
            HttpStatus status, String code, String message, Map<String, String> fields
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", code);
        body.put("message", message);
        if (fields != null) {
            body.put("fields", fields);
        }
        return ResponseEntity.status(status).body(body);
    }
}
