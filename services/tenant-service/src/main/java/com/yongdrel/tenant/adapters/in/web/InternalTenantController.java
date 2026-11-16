package com.yongdrel.tenant.adapters.in.web;

import com.yongdrel.tenant.application.service.TenantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequestMapping("/internal/tenants")
public class InternalTenantController {
    private final TenantService service;
    private final byte[] key;

    public InternalTenantController(TenantService service,
                                    @Value("${app.internal.api-key}") String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Tenant internal API key is required");
        }
        this.service = service;
        key = configured.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/{tenantId}/payment-details")
    public PaymentDetails paymentDetails(
            @PathVariable UUID tenantId,
            @RequestParam UUID organizerId,
            @RequestHeader(value = "X-Internal-API-Key", required = false) String provided) {
        if (provided == null || !MessageDigest.isEqual(
                key, provided.getBytes(StandardCharsets.UTF_8))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid internal credentials");
        }
        var tenant = service.getPaymentDetails(tenantId, organizerId);
        if (tenant.getPaymentAccountReference() == null
                || tenant.getPaymentAccountReference().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Payment destination is not configured");
        }
        return new PaymentDetails(tenant.getTenantId(), organizerId,
                tenant.getPaymentDestinationName(), tenant.getPaymentAccountReference(),
                tenant.getPaymentInstructions());
    }

    public record PaymentDetails(UUID tenantId, UUID organizerId,
                                 String destinationName, String accountReference,
                                 String instructions) {}
}
