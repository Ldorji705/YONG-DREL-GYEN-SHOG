package com.yongdrel.refund_service.adapter.in.web.exception;

import com.yongdrel.refund_service.application.exception.DependencyUnavailableException;
import com.yongdrel.refund_service.application.exception.IdempotencyKeyConflictException;
import com.yongdrel.refund_service.application.exception.InvalidRefundStateException;
import com.yongdrel.refund_service.application.exception.OverRefundException;
import com.yongdrel.refund_service.application.exception.PaymentNotFoundException;
import com.yongdrel.refund_service.application.exception.RefundNotAllowedException;
import com.yongdrel.refund_service.application.exception.RefundNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Clear error codes, no stack traces (handbook section 9). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({RefundNotFoundException.class, PaymentNotFoundException.class})
    ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(OverRefundException.class)
    ResponseEntity<Map<String, Object>> overRefund(OverRefundException ex) {
        return body(HttpStatus.UNPROCESSABLE_CONTENT, "OVER_REFUND", ex.getMessage(), null);
    }

    @ExceptionHandler(RefundNotAllowedException.class)
    ResponseEntity<Map<String, Object>> notAllowed(RefundNotAllowedException ex) {
        return body(HttpStatus.UNPROCESSABLE_CONTENT, "PAYMENT_NOT_REFUNDABLE", ex.getMessage(), null);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    ResponseEntity<Map<String, Object>> keyConflict(IdempotencyKeyConflictException ex) {
        return body(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidRefundStateException.class)
    ResponseEntity<Map<String, Object>> badState(InvalidRefundStateException ex) {
        return body(HttpStatus.CONFLICT, "INVALID_REFUND_STATE", ex.getMessage(), null);
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    ResponseEntity<Map<String, Object>> unavailable(DependencyUnavailableException ex) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", ex.getMessage(), null);
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

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> malformed(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request is missing or has invalid values", null);
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
