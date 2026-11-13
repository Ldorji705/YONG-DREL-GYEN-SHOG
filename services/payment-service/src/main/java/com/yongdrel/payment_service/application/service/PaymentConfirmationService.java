package com.yongdrel.payment_service.application.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.yongdrel.payment_service.adapter.out.persistence.*;
import com.yongdrel.payment_service.application.port.*;
import com.yongdrel.payment_service.domain.model.PaymentStatus;
import com.yongdrel.payment_service.dto.PaymentConfirmationEvent;
import com.yongdrel.payment_service.dto.PaymentResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentConfirmationService {

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final PaymentVerificationPort verificationPort;
    private final ObjectMapper objectMapper;

    public PaymentConfirmationService(
            PaymentRepository paymentRepository,
            PaymentOutboxRepository outboxRepository,
            PaymentVerificationPort verificationPort,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.verificationPort = verificationPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse confirmVerifiedPayment(UUID paymentId) {

        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            return PaymentResponse.from(payment);
        }

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment cannot be confirmed"
            );
        }

        VerifiedPayment verified =
                verificationPort.verifyPayment(paymentId);

        if (verified == null
                || !verified.successful()
                || !paymentId.equals(verified.paymentId())
                || verified.providerReference() == null
                || verified.providerReference().isBlank()
                || verified.amount() == null
                || verified.currency() == null
                || payment.getAmount().compareTo(verified.amount()) != 0
                || !payment.getCurrency().equals(verified.currency())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment verification failed"
            );
        }

        payment.markConfirmed(verified.providerReference());

        PaymentConfirmationEvent event = new PaymentConfirmationEvent(
                UUID.randomUUID(),
                payment.getId(),
                payment.getOrderId(),
                payment.getLotteryEventId(),
                payment.getUserId(),
                payment.getQuantity(),
                LocalDateTime.now()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);

            outboxRepository.save(
                    new PaymentOutboxEntity(
                            payment.getId(),
                            "payment.confirmed",
                            payload
                    )
            );

        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to serialize payment event",
                    exception
            );
        }

        return PaymentResponse.from(paymentRepository.save(payment));
    }
}
