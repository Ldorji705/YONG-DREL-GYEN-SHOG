package com.yongdrel.payment_service.adapter.out.bank;

import com.yongdrel.payment_service.application.port.BankTransactionVerificationPort;
import com.yongdrel.payment_service.application.port.BankTransactionVerificationRequest;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BankTransactionServiceAdapter
        implements BankTransactionVerificationPort {

    @Override
    public boolean verify(BankTransactionVerificationRequest request) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Bank transaction verification is not configured"
        );
    }
}
