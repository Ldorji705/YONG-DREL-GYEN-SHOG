package com.yongdrel.wallet_service.adapter.in.web.dto;

import com.yongdrel.wallet_service.domain.model.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(UUID id, String ownerId, String currency, BigDecimal balance, Instant updatedAt) {

    public static WalletResponse from(Wallet w) {
        return new WalletResponse(w.getId(), w.getOwnerId(), w.getCurrency(), w.getBalance(), w.getUpdatedAt());
    }
}
