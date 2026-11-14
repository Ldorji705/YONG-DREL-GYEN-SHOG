package com.yongdrel.refund_service.application.port;

import java.util.UUID;

public interface PaymentLookupPort {

    /**
     * Looks up a payment as the calling user (their JWT is forwarded), so the
     * Payment Service's own ownership check also applies.
     */
    PaymentSnapshot getPayment(UUID paymentId, String bearerToken);
}
