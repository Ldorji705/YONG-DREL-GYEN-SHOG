package com.yongdrel.payment_service.adapter.out.provider;

import com.yongdrel.payment_service.application.port.PaymentVerificationPort;
import com.yongdrel.payment_service.application.port.VerifiedPayment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class PaymentProviderAdapter implements PaymentVerificationPort {

    @Override
    public VerifiedPayment verifyPayment(UUID paymentId) {

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payment provider verification is not configured"
        );
    }
}
