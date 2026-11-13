package com.yongdrel.payment_service.application.service;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentEntity;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxEntity;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxRepository;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentRepository;
import com.yongdrel.payment_service.application.port.*;
import com.yongdrel.payment_service.domain.model.PaymentStatus;
import com.yongdrel.payment_service.dto.PaymentConfirmationEvent;
import com.yongdrel.payment_service.dto.PaymentResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ManualPaymentVerificationService {

    private static final String CONFIRMED_EVENT = "payment.confirmed";

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final PaymentProofValidationPort proofValidationPort;
    private final EventOwnershipVerificationPort ownershipPort;
    private final BankTransactionVerificationPort bankVerificationPort;
    private final ObjectMapper objectMapper;

    public ManualPaymentVerificationService(
            PaymentRepository paymentRepository,
            PaymentOutboxRepository outboxRepository,
            PaymentProofValidationPort proofValidationPort,
            EventOwnershipVerificationPort ownershipPort,
            BankTransactionVerificationPort bankVerificationPort,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.proofValidationPort = proofValidationPort;
        this.ownershipPort = ownershipPort;
        this.bankVerificationPort = bankVerificationPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse approve(UUID paymentId, UUID organizerId) {
        PaymentEntity payment = lockedPayment(paymentId);
        authorizeOwnership(payment, organizerId);

        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            if (organizerId.equals(payment.getVerifiedBy())) {
                return PaymentResponse.from(payment);
            }
            throw conflict("Payment was approved by another organizer");
        }

        requirePendingVerification(payment);
        validateProof(payment);

        if (!bankVerificationPort.verify(
                new BankTransactionVerificationRequest(
                        payment.getId(),
                        payment.getOrderId(),
                        payment.getPaymentProofId(),
                        organizerId
                )
        )) {
            throw conflict("Bank transaction could not be verified");
        }

        payment.markApproved(organizerId);
        createConfirmationOutbox(payment);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse reject(
            UUID paymentId,
            UUID organizerId,
            String reason
    ) {
        PaymentEntity payment = lockedPayment(paymentId);
        authorizeOwnership(payment, organizerId);
        requirePendingVerification(payment);

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is required"
            );
        }

        payment.markRejected(organizerId, reason.trim());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    private PaymentEntity lockedPayment(UUID paymentId) {
        if (paymentId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment ID is required"
            );
        }

        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));
    }

    private void authorizeOwnership(
            PaymentEntity payment,
            UUID organizerId
    ) {
        if (organizerId == null
                || !ownershipPort.ownsEvent(
                        new EventOwnershipVerificationRequest(
                                payment.getLotteryEventId(),
                                organizerId
                        ))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organizer is not authorized for this event"
            );
        }
    }

    private void requirePendingVerification(PaymentEntity payment) {
        if (payment.getStatus() != PaymentStatus.PENDING_VERIFICATION) {
            throw conflict("Payment is not awaiting verification");
        }
    }

    private void validateProof(PaymentEntity payment) {
        if (payment.getPaymentProofId() == null) {
            throw conflict("Payment has no validated proof");
        }

        PaymentProofValidationResult result =
                proofValidationPort.validate(
                        new PaymentProofValidationRequest(
                                payment.getId(),
                                payment.getOrderId(),
                                payment.getUserId(),
                                payment.getPaymentProofId()
                        )
                );

        if (result == null
                || !result.exists()
                || !result.belongsToStudent()
                || !result.belongsToPayment()
                || !result.active()
                || !result.eligibleForReview()) {
            throw conflict("Payment proof is not valid for approval");
        }
    }

    private void createConfirmationOutbox(PaymentEntity payment) {
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
            outboxRepository.save(new PaymentOutboxEntity(
                    payment.getId(),
                    CONFIRMED_EVENT,
                    objectMapper.writeValueAsString(event)
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to serialize payment confirmation event",
                    exception
            );
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
