package com.yongdrel.payment_service.adapter.in.web;

import com.yongdrel.payment_service.application.service.PaymentApplicationService;
import com.yongdrel.payment_service.dto.PaymentRequest;
import com.yongdrel.payment_service.dto.PaymentResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentApplicationService paymentService;

    public PaymentController(
            PaymentApplicationService paymentService
    ) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentResponse initiatePayment(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.initiatePayment(
                request.orderId(),
                authenticatedUserId(jwt)
        );
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.getPayment(
                paymentId,
                authenticatedUserId(jwt)
        );
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse getPaymentByOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.getPaymentByOrder(
                orderId,
                authenticatedUserId(jwt)
        );
    }

    private Long authenticatedUserId(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        try {
            Long userId = Long.valueOf(jwt.getSubject());

            if (userId <= 0) {
                throw new NumberFormatException(
                        "Invalid user ID"
                );
            }

            return userId;

        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user identity"
            );
        }
    }
}
