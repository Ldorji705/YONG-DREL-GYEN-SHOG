package com.yongdrel.profile.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @Size(max = 30)
        String phoneNumber,

        @Size(max = 255)
        String address

) {}