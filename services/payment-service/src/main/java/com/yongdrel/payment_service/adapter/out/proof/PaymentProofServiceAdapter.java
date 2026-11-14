package com.yongdrel.payment_service.adapter.out.proof;

import com.yongdrel.payment_service.application.port.PaymentProofValidationPort;
import com.yongdrel.payment_service.application.port.PaymentProofValidationRequest;
import com.yongdrel.payment_service.application.port.PaymentProofValidationResult;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentProofServiceAdapter
        implements PaymentProofValidationPort {

    @Override
    public PaymentProofValidationResult validate(
            PaymentProofValidationRequest request
    ) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payment Proof Service integration is not configured"
        );
    }
}
