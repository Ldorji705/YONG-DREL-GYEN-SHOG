package com.yongdrel.payment_service.application.port;

public interface PaymentProofValidationPort {

    PaymentProofValidationResult validate(
            PaymentProofValidationRequest request
    );
}
