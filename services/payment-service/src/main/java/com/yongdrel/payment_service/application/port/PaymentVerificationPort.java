package com.yongdrel.payment_service.application.port;

import java.util.UUID;

public interface PaymentVerificationPort {

    VerifiedPayment verifyPayment(UUID paymentId);
}
