package com.yongdrel.tenant.adapters.in.web.dto;

import com.yongdrel.tenant.domain.model.Tenant;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID tenantId,
        String name,
        UUID ownerUserId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getTenantId(),
                tenant.getName(),
                tenant.getOwnerUserId(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}