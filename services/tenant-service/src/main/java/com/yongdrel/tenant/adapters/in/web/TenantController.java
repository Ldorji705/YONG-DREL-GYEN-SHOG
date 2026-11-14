package com.yongdrel.tenant.adapters.in.web;

import com.yongdrel.tenant.adapters.in.web.dto.CreateTenantRequest;
import com.yongdrel.tenant.adapters.in.web.dto.TenantResponse;
import com.yongdrel.tenant.adapters.in.web.dto.UpdateTenantRequest;
import com.yongdrel.tenant.application.service.TenantService;
import com.yongdrel.tenant.domain.model.Tenant;
import com.yongdrel.tenant.adapters.in.web.dto.UpdateTenantStatusRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest request,
            JwtAuthenticationToken authentication) {

        UUID ownerUserId = getAuthenticatedUserId(authentication);

        Tenant tenant = tenantService.createTenant(
                request.name(),
                ownerUserId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TenantResponse.from(tenant));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenant(
            @PathVariable UUID tenantId,
            JwtAuthenticationToken authentication) {

        UUID ownerUserId = getAuthenticatedUserId(authentication);

        Tenant tenant = tenantService.getTenant(
                tenantId,
                ownerUserId
        );

        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

    @GetMapping("/me")
    public ResponseEntity<List<TenantResponse>> getMyTenants(
            JwtAuthenticationToken authentication) {

        UUID ownerUserId = getAuthenticatedUserId(authentication);

        List<TenantResponse> tenants = tenantService
                .getMyTenants(ownerUserId)
                .stream()
                .map(TenantResponse::from)
                .toList();

        return ResponseEntity.ok(tenants);
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request,
            JwtAuthenticationToken authentication) {

        UUID ownerUserId = getAuthenticatedUserId(authentication);

        Tenant tenant = tenantService.updateTenant(
                tenantId,
                request.name(),
                ownerUserId
        );

        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

        @PatchMapping("/{tenantId}/status")
    public ResponseEntity<TenantResponse> updateTenantStatus(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantStatusRequest request) {

        Tenant tenant = tenantService.updateTenantStatus(
                tenantId,
                request.status()
        );

        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

    private UUID getAuthenticatedUserId(
            JwtAuthenticationToken authentication) {

        return UUID.fromString(authentication.getToken().getSubject());
    }
}
