package com.yongdrel.tenant.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTenantStatusRequest(

        @NotBlank(message = "Tenant status is required")
        @Pattern(
                regexp = "(?i)ACTIVE|INACTIVE|SUSPENDED",
                message = "Status must be ACTIVE, INACTIVE, or SUSPENDED"
        )
        String status

) {
}
