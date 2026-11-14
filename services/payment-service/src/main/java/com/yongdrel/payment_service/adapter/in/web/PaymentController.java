package com.yongdrel.payment_service.adapter.in.web;

import com.yongdrel.payment_service.application.service.PaymentApplicationService;
import com.yongdrel.payment_service.application.service.ManualPaymentVerificationService;
import com.yongdrel.payment_service.application.service.OrganizerRolePolicy;
import com.yongdrel.payment_service.dto.PaymentRequest;
import com.yongdrel.payment_service.dto.PaymentRejectionRequest;
import com.yongdrel.payment_service.dto.PaymentResponse;
import com.yongdrel.payment_service.dto.PaymentProofRequest;

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
    private final ManualPaymentVerificationService verificationService;
    private final OrganizerRolePolicy organizerRolePolicy;

    public PaymentController(
            PaymentApplicationService paymentService,
            ManualPaymentVerificationService verificationService,
            OrganizerRolePolicy organizerRolePolicy
    ) {
        this.paymentService = paymentService;
        this.verificationService = verificationService;
        this.organizerRolePolicy = organizerRolePolicy;
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

    @PostMapping("/{paymentId}/proof")
    public PaymentResponse submitPaymentProof(
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentProofRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.submitPaymentProof(
                paymentId,
                authenticatedUserId(jwt),
                request.paymentProofId()
        );
    }

    @PostMapping("/{paymentId}/approve")
    public PaymentResponse approvePayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID organizerId = authenticatedOrganizerId(jwt);
        return verificationService.approve(paymentId, organizerId);
    }

    @PostMapping("/{paymentId}/reject")
    public PaymentResponse rejectPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentRejectionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID organizerId = authenticatedOrganizerId(jwt);
        return verificationService.reject(
                paymentId,
                organizerId,
                request.reason()
        );
    }

    private UUID authenticatedUserId(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user identity"
            );
        }
    }

    private UUID authenticatedOrganizerId(Jwt jwt) {
            UUID userId = authenticatedUserId(jwt);
            String role = jwt.getClaimAsString("role");

            if (!organizerRolePolicy.isOrganizer(role)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Organizer role required"
                );
            }

            return userId;
    }
}
