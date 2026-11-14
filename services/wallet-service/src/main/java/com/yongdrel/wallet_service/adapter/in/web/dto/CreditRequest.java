package com.yongdrel.wallet_service.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of POST /api/wallets/credit (internal services or ADMIN only). */
public record CreditRequest(

        @NotBlank @Size(max = 100)
        String ownerId,

        @NotNull
        @DecimalMin(value = "0.0001")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Size(max = 255)
        String description
) {
}
