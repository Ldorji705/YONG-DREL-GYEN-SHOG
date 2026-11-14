package com.yongdrel.tenant.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(

        @NotBlank(message = "Tenant name is required")
        @Size(max = 150, message = "Tenant name cannot exceed 150 characters")
        String name

) {
}