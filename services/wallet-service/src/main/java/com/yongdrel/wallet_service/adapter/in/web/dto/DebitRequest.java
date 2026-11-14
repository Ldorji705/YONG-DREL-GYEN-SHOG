package com.yongdrel.wallet_service.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of POST /api/wallets/debit. The wallet is always the caller's own (from the JWT). */
public record DebitRequest(

        @NotNull
        @DecimalMin(value = "0.0001")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Size(max = 255)
        String description
) {
}
