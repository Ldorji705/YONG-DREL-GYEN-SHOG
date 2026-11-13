package com.yongdrel.payment_service.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentProofRequest(
        @NotNull(message = "Payment proof ID is required")
        UUID paymentProofId
) {}
