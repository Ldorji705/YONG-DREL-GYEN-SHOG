package com.yongdrel.authservice.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OrganizerProvisioningRequest(
        @NotBlank @Email String email
) {}
