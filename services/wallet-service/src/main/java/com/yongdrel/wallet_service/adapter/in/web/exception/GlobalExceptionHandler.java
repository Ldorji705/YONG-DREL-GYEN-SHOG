package com.yongdrel.wallet_service.adapter.in.web.exception;

import com.yongdrel.wallet_service.application.exception.IdempotencyKeyConflictException;
import com.yongdrel.wallet_service.application.exception.InsufficientFundsException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Clear error codes, no stack traces (handbook section 9). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<Map<String, Object>> insufficient(InsufficientFundsException ex) {
        return body(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS", ex.getMessage(), null);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    ResponseEntity<Map<String, Object>> keyConflict(IdempotencyKeyConflictException ex) {
        return body(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fields);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class})
    ResponseEntity<Map<String, Object>> invalidHeader(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Idempotency-Key header is required (1-100 characters)", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> malformed(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or invalid", null);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code,
                                                     String message, Map<String, String> fields) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("timestamp", Instant.now());
        b.put("status", status.value());
        b.put("error", code);
        b.put("message", message);
        if (fields != null) {
            b.put("fields", fields);
        }
        return ResponseEntity.status(status).body(b);
    }
}
