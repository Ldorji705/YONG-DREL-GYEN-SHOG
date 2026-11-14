package com.yongdrel.wallet_service.adapter.in.web.dto;

import com.yongdrel.wallet_service.domain.model.OperationType;
import com.yongdrel.wallet_service.domain.model.WalletOperation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OperationResponse(
        UUID operationId,
        UUID walletId,
        OperationType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt
) {
    public static OperationResponse from(WalletOperation op) {
        return new OperationResponse(op.getId(), op.getWalletId(), op.getType(), op.getAmount(),
                op.getBalanceAfter(), op.getDescription(), op.getCreatedAt());
    }
}
