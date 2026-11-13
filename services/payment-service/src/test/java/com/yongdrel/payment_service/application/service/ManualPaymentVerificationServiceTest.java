package com.yongdrel.payment_service.application.service;

import com.yongdrel.payment_service.adapter.out.persistence.*;
import com.yongdrel.payment_service.application.port.*;
import com.yongdrel.payment_service.domain.model.PaymentStatus;
import com.yongdrel.payment_service.dto.PaymentResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ManualPaymentVerificationServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentOutboxRepository outboxRepository;
    private PaymentProofValidationPort proofPort;
    private EventOwnershipVerificationPort ownershipPort;
    private BankTransactionVerificationPort bankPort;
    private ManualPaymentVerificationService service;
    private PaymentEntity payment;
    private UUID organizerId;
    private UUID proofId;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        outboxRepository = mock(PaymentOutboxRepository.class);
        proofPort = mock(PaymentProofValidationPort.class);
        ownershipPort = mock(EventOwnershipVerificationPort.class);
        bankPort = mock(BankTransactionVerificationPort.class);
        service = new ManualPaymentVerificationService(
                paymentRepository,
                outboxRepository,
                proofPort,
                ownershipPort,
                bankPort,
                new ObjectMapper()
        );
        payment = new PaymentEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                new BigDecimal("200.00"),
                "BTN"
        );
        proofId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        payment.markPendingVerification(proofId);

        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));
        when(ownershipPort.ownsEvent(any())).thenReturn(true);
        when(proofPort.validate(any())).thenReturn(
                new PaymentProofValidationResult(
                        true, true, true, true, true
                )
        );
        when(bankPort.verify(any())).thenReturn(true);
        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void approvesAuthorizedPaymentAndCreatesOneOutboxEvent() {
        PaymentResponse response = service.approve(
                payment.getId(), organizerId
        );

        assertEquals(PaymentStatus.CONFIRMED, response.status());
        assertEquals(organizerId, payment.getVerifiedBy());
        verify(paymentRepository).findByIdForUpdate(payment.getId());
        verify(outboxRepository).save(any(PaymentOutboxEntity.class));
        verify(paymentRepository).save(payment);
    }

    @Test
    void deniesUnauthorizedOrganizer() {
        when(ownershipPort.ownsEvent(any())).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(payment.getId(), organizerId)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(PaymentStatus.PENDING_VERIFICATION, payment.getStatus());
        verifyNoInteractions(proofPort, bankPort, outboxRepository);
    }

    @Test
    void failsClosedWhenOwnershipServiceUnavailable() {
        when(ownershipPort.ownsEvent(any())).thenThrow(
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "ownership unavailable"
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(payment.getId(), organizerId)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void failsClosedWhenProofServiceUnavailable() {
        when(proofPort.validate(any())).thenThrow(
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "proof unavailable"
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(payment.getId(), organizerId)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        verifyNoInteractions(bankPort, outboxRepository);
    }

    @Test
    void rejectsPaymentWithoutProof() {
        payment = new PaymentEntity(
                payment.getOrderId(),
                payment.getLotteryEventId(),
                payment.getUserId(),
                2,
                payment.getAmount(),
                payment.getCurrency()
        );
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));
        payment.markPendingVerification(proofId);

        // Clear the association to model a corrupt/unmigrated record.
        PaymentEntity recordWithoutProof = mock(PaymentEntity.class);
        when(recordWithoutProof.getStatus())
                .thenReturn(PaymentStatus.PENDING_VERIFICATION);
        when(recordWithoutProof.getPaymentProofId()).thenReturn(null);
        when(recordWithoutProof.getId()).thenReturn(payment.getId());
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(recordWithoutProof));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(payment.getId(), organizerId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(bankPort, outboxRepository);
    }

    @Test
    void rejectsBankTransactionThatCannotBeVerified() {
        when(bankPort.verify(any())).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(payment.getId(), organizerId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(PaymentStatus.PENDING_VERIFICATION, payment.getStatus());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void repeatedApprovalBySameOrganizerIsIdempotent() {
        service.approve(payment.getId(), organizerId);
        reset(outboxRepository, paymentRepository);
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = service.approve(
                payment.getId(), organizerId
        );

        assertEquals(PaymentStatus.CONFIRMED, response.status());
        verifyNoInteractions(outboxRepository);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void repeatedApprovalByDifferentOrganizerIsRejected() {
        service.approve(payment.getId(), organizerId);
        UUID anotherOrganizer = UUID.randomUUID();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(payment.getId(), anotherOrganizer)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void rejectsApprovalAfterRejection() {
        payment.markRejected(organizerId, "Transfer not found");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.approve(payment.getId(), organizerId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void rejectsPaymentAndDoesNotCreateOutboxEvent() {
        PaymentResponse response = service.reject(
                payment.getId(), organizerId, "Bank transfer could not be verified"
        );

        assertEquals(PaymentStatus.REJECTED, response.status());
        assertEquals(
                "Bank transfer could not be verified",
                payment.getRejectionReason()
        );
        verify(paymentRepository).save(payment);
        verifyNoInteractions(outboxRepository, proofPort, bankPort);
    }

    @Test
    void rejectsMissingReason() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reject(payment.getId(), organizerId, " ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(PaymentStatus.PENDING_VERIFICATION, payment.getStatus());
    }

    @Test
    void rejectsConflictingRepeatedDecision() {
        service.reject(payment.getId(), organizerId, "Not verified");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.reject(
                        payment.getId(), organizerId, "Another reason"
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void rollsBackApprovalWhenOutboxSaveFails() {
        when(outboxRepository.save(any(PaymentOutboxEntity.class)))
                .thenThrow(new IllegalStateException("outbox failure"));

        assertThrows(
                IllegalStateException.class,
                () -> service.approve(payment.getId(), organizerId)
        );

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void usesPessimisticLockForApproval() {
        service.approve(payment.getId(), organizerId);

        verify(paymentRepository).findByIdForUpdate(payment.getId());
    }
}
