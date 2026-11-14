package com.yongdrel.refund_service.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of POST /api/refunds. The caller is taken from the JWT, never from the body. */
public record CreateRefundRequest(

        @NotNull
        UUID paymentId,

        @NotNull
        @DecimalMin(value = "0.0001")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Size(max = 255)
        String reason
) {
}
