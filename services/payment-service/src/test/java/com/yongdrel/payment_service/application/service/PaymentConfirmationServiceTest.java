package com.yongdrel.payment_service.application.service;

import com.yongdrel.payment_service.adapter.out.persistence.*;
import com.yongdrel.payment_service.application.port.*;
import com.yongdrel.payment_service.domain.model.PaymentStatus;
import com.yongdrel.payment_service.dto.PaymentResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOutboxRepository outboxRepository;

    @Mock
    private PaymentVerificationPort verificationPort;

    private PaymentConfirmationService service;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        service = new PaymentConfirmationService(
                paymentRepository,
                outboxRepository,
                verificationPort,
                new ObjectMapper()
        );

        payment = new PaymentEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                101L,
                2,
                new BigDecimal("200.00"),
                "BTN"
        );
    }

    private void mockPendingPayment() {
        when(paymentRepository.findByIdForUpdate(payment.getId()))
                .thenReturn(Optional.of(payment));
    }

    private VerifiedPayment verified(
            BigDecimal amount,
            String currency,
            boolean successful
    ) {
        return new VerifiedPayment(
                payment.getId(),
                "PROVIDER-" + UUID.randomUUID(),
                amount,
                currency,
                successful
        );
    }

    @Test
    void shouldConfirmPaymentAndCreateOutboxEvent() {
        mockPendingPayment();

        when(verificationPort.verifyPayment(payment.getId()))
                .thenReturn(verified(
                        new BigDecimal("200.00"), "BTN", true
                ));

        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response =
                service.confirmVerifiedPayment(payment.getId());

        assertEquals(PaymentStatus.CONFIRMED, response.status());

        verify(outboxRepository).save(
                any(PaymentOutboxEntity.class)
        );

        verify(paymentRepository).save(payment);
    }

    @Test
    void shouldRejectUnsuccessfulVerification() {
        mockPendingPayment();

        when(verificationPort.verifyPayment(payment.getId()))
                .thenReturn(verified(
                        new BigDecimal("200.00"), "BTN", false
                ));

        assertThrows(
                ResponseStatusException.class,
                () -> service.confirmVerifiedPayment(payment.getId())
        );

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void shouldRejectIncorrectAmount() {
        mockPendingPayment();

        when(verificationPort.verifyPayment(payment.getId()))
                .thenReturn(verified(
                        new BigDecimal("100.00"), "BTN", true
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.confirmVerifiedPayment(payment.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void shouldRejectIncorrectCurrency() {
        mockPendingPayment();

        when(verificationPort.verifyPayment(payment.getId()))
                .thenReturn(verified(
                        new BigDecimal("200.00"), "USD", true
                ));

        assertThrows(
                ResponseStatusException.class,
                () -> service.confirmVerifiedPayment(payment.getId())
        );

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void shouldNotCreateDuplicateEvent() {
        payment.markConfirmed("EXISTING-REFERENCE");
        mockPendingPayment();

        PaymentResponse response =
                service.confirmVerifiedPayment(payment.getId());

        assertEquals(PaymentStatus.CONFIRMED, response.status());

        verifyNoInteractions(verificationPort, outboxRepository);
        verify(paymentRepository, never())
                .save(any(PaymentEntity.class));
    }

    @Test
    void shouldFailWhenOutboxSaveFails() {
        mockPendingPayment();

        when(verificationPort.verifyPayment(payment.getId()))
                .thenReturn(verified(
                        new BigDecimal("200.00"), "BTN", true
                ));

        when(outboxRepository.save(any(PaymentOutboxEntity.class)))
                .thenThrow(new IllegalStateException(
                        "Database write failed"
                ));

        assertThrows(
                IllegalStateException.class,
                () -> service.confirmVerifiedPayment(payment.getId())
        );

        verify(paymentRepository, never())
                .save(any(PaymentEntity.class));
    }
}
