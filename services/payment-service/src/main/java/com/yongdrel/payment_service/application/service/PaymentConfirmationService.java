package com.yongdrel.payment_service.application.service;

import tools.jackson.databind.ObjectMapper;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxRepository;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentRepository;
import com.yongdrel.payment_service.application.port.PaymentVerificationPort;
import com.yongdrel.payment_service.dto.PaymentResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentConfirmationService {

    public PaymentConfirmationService(
            PaymentRepository paymentRepository,
            PaymentOutboxRepository outboxRepository,
            PaymentVerificationPort verificationPort,
            ObjectMapper objectMapper
    ) {
    }

    @Transactional
    public PaymentResponse confirmVerifiedPayment(UUID paymentId) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payment confirmation is disabled until the RabbitMQ "
                        + "identity contract supports UUID user identities"
        );

    }
}
