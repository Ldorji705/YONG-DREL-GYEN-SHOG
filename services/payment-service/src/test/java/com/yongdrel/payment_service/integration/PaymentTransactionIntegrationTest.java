package com.yongdrel.payment_service.integration;

import com.yongdrel.payment_service.adapter.out.persistence.*;
import com.yongdrel.payment_service.application.port.*;
import com.yongdrel.payment_service.application.service.PaymentConfirmationService;
import com.yongdrel.payment_service.domain.model.PaymentStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("dbtest")
class PaymentTransactionIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxRepository outboxRepository;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @MockitoBean
    private PaymentVerificationPort verificationPort;

    @Test
    void shouldRollbackPaymentWhenOutboxInsertFails() {

        PaymentEntity payment = paymentRepository.saveAndFlush(
                new PaymentEntity(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        101L,
                        2,
                        new BigDecimal("200.00"),
                        "BTN"
                )
        );

        UUID paymentId = payment.getId();

        // Pre-create an outbox record for this payment.
        // A second insert must violate the unique paymentId constraint.
        outboxRepository.saveAndFlush(
                new PaymentOutboxEntity(
                        paymentId,
                        "payment.confirmed",
                        "{}"
                )
        );

        when(verificationPort.verifyPayment(paymentId))
                .thenReturn(new VerifiedPayment(
                        paymentId,
                        "TEST-" + UUID.randomUUID(),
                        new BigDecimal("200.00"),
                        "BTN",
                        true
                ));

        assertThrows(
                RuntimeException.class,
                () -> confirmationService.confirmVerifiedPayment(paymentId)
        );

        // Reload from MySQL after the failed transaction.
        PaymentEntity reloaded =
                paymentRepository.findById(paymentId).orElseThrow();

        assertEquals(PaymentStatus.PENDING, reloaded.getStatus());

        // The original outbox row must remain the only one.
        long count = outboxRepository.findAll().stream()
                .filter(event -> paymentId.equals(event.getPaymentId()))
                .count();

        assertEquals(1, count);
    }
}
