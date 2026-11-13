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
    void shouldFailClosedBeforePaymentMutation() {

        PaymentEntity payment = paymentRepository.saveAndFlush(
                new PaymentEntity(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        2,
                        new BigDecimal("200.00"),
                        "BTN"
                )
        );

        UUID paymentId = payment.getId();

        var exception = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> confirmationService.confirmVerifiedPayment(paymentId)
        );

        assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        PaymentEntity reloaded =
                paymentRepository.findById(paymentId).orElseThrow();

        assertEquals(PaymentStatus.PENDING, reloaded.getStatus());
        assertEquals(0, outboxRepository.count());
    }
}
