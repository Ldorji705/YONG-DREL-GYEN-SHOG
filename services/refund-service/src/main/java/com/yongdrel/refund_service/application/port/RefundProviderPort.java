package com.yongdrel.refund_service.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundProviderPort {

    /** Sends the refund to the provider and returns the provider's reference. Idempotent per refundId. */
    String refund(UUID refundId, BigDecimal amount, String currency);
}
