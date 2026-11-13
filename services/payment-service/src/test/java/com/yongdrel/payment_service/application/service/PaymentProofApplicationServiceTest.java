package com.yongdrel.payment_service.application.service;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentEntity;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentRepository;
import com.yongdrel.payment_service.application.port.PaymentProofValidationPort;
import com.yongdrel.payment_service.application.port.PaymentProofValidationRequest;
import com.yongdrel.payment_service.application.port.PaymentProofValidationResult;
import com.yongdrel.payment_service.application.port.OrderServicePort;
import com.yongdrel.payment_service.domain.model.PaymentStatus;
import com.yongdrel.payment_service.dto.PaymentResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentProofApplicationServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentProofValidationPort proofValidationPort;
    private PaymentApplicationService service;
    private PaymentEntity payment;
    private UUID studentId;
    private UUID proofId;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        proofValidationPort = mock(PaymentProofValidationPort.class);
        studentId = UUID.randomUUID();
        proofId = UUID.randomUUID();
        payment = new PaymentEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                studentId,
                2,
                new BigDecimal("200.00"),
                "BTN"
        );

        service = new PaymentApplicationService(
                paymentRepository,
                mock(OrderServicePort.class),
                proofValidationPort
        );

        when(paymentRepository.findByIdForProofUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));
    }

    @Test
    void associatesTrustedProofAndMovesPaymentToVerification() {
        when(proofValidationPort.validate(any()))
                .thenReturn(validProof());

        PaymentResponse response = service.submitPaymentProof(
                payment.getId(), studentId, proofId
        );

        assertEquals(PaymentStatus.PENDING_VERIFICATION, response.status());
        assertEquals(proofId, response.paymentProofId());
        verify(paymentRepository).save(payment);
        verify(proofValidationPort).validate(
                new PaymentProofValidationRequest(
                        payment.getId(),
                        payment.getOrderId(),
                        studentId,
                        proofId
                )
        );
    }

    @Test
    void rejectsAnotherStudentWithoutCallingProofService() {
        UUID anotherStudent = UUID.randomUUID();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), anotherStudent, proofId
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(proofValidationPort);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidProofWithoutChangingPayment() {
        when(proofValidationPort.validate(any()))
                .thenReturn(new PaymentProofValidationResult(
                        true, true, false, true, true
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), studentId, proofId
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsProofBelongingToAnotherStudent() {
        when(proofValidationPort.validate(any()))
                .thenReturn(new PaymentProofValidationResult(
                        true, false, true, true, true
                ));

        assertThrows(
                ResponseStatusException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), studentId, proofId
                )
        );

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsProofBelongingToAnotherPayment() {
        when(proofValidationPort.validate(any()))
                .thenReturn(new PaymentProofValidationResult(
                        true, true, false, true, true
                ));

        assertThrows(
                ResponseStatusException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), studentId, proofId
                )
        );

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void propagatesUnavailableProofServiceAndLeavesPaymentPending() {
        ResponseStatusException unavailable = new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Proof service unavailable"
        );
        when(proofValidationPort.validate(any())).thenThrow(unavailable);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), studentId, proofId
                )
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void acceptsIdempotentResubmissionOfSameValidatedProof() {
        payment.markPendingVerification(proofId);
        when(proofValidationPort.validate(any()))
                .thenReturn(validProof());

        PaymentResponse response = service.submitPaymentProof(
                payment.getId(), studentId, proofId
        );

        assertEquals(PaymentStatus.PENDING_VERIFICATION, response.status());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejectsConflictingProof() {
        payment.markPendingVerification(proofId);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), studentId, UUID.randomUUID()
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(proofValidationPort);
    }

    @Test
    void rejectsNonPendingStates() {
        payment.markPendingVerification(proofId);
        payment.markRejected(UUID.randomUUID(), "Unreadable proof");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), studentId, proofId
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(proofValidationPort);
    }

    @Test
    void rejectsConfirmedCancelledAndRefundedStates() {
        assertRejectsInState(
                entity -> entity.markConfirmed("legacy-provider-reference")
        );
        assertRejectsInState(PaymentEntity::markCancelled);
        assertRejectsInState(PaymentEntity::markRefunded);
        verifyNoInteractions(proofValidationPort);
    }

    private void assertRejectsInState(
            Consumer<PaymentEntity> transition
    ) {
        payment = new PaymentEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                studentId,
                2,
                new BigDecimal("200.00"),
                "BTN"
        );
        when(paymentRepository.findByIdForProofUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));
        transition.accept(payment);

        assertThrows(
                ResponseStatusException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), studentId, proofId
                )
        );
    }

    @Test
    void usesPessimisticLockLookupForEverySubmission() {
        when(proofValidationPort.validate(any()))
                .thenReturn(validProof());

        service.submitPaymentProof(payment.getId(), studentId, proofId);

        verify(paymentRepository).findByIdForProofUpdate(payment.getId());
    }

    @Test
    void propagatesPaymentSaveFailure() {
        when(proofValidationPort.validate(any()))
                .thenReturn(validProof());
        when(paymentRepository.save(payment))
                .thenThrow(new IllegalStateException("database failure"));

        assertThrows(
                IllegalStateException.class,
                () -> service.submitPaymentProof(
                        payment.getId(), studentId, proofId
                )
        );

        assertEquals(PaymentStatus.PENDING_VERIFICATION, payment.getStatus());
        assertEquals(proofId, payment.getPaymentProofId());
    }

    private PaymentProofValidationResult validProof() {
        return new PaymentProofValidationResult(
                true, true, true, true, true
        );
    }
}
