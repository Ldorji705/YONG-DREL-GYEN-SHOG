package com.yongdrel.payment_service.adapter.out.persistence;

import com.yongdrel.payment_service.domain.model.PaymentStatus;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dbtest")
@Transactional
class PaymentRepositoryIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldSaveAndRetrievePayment() {

        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PaymentEntity payment = new PaymentEntity(
                orderId,
                UUID.randomUUID(),
                userId,
                2,
                new BigDecimal("200.00"),
                "BTN"
        );

        PaymentEntity saved = paymentRepository.saveAndFlush(payment);

        PaymentEntity found = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals(PaymentStatus.PENDING, found.getStatus());
        assertEquals(userId, found.getUserId());
        assertEquals(new BigDecimal("200.00"), found.getAmount());
        assertEquals("BTN", found.getCurrency());
    }

    @Test
    void shouldPreventDuplicateOrderPayments() {

        UUID orderId = UUID.randomUUID();

        PaymentEntity first = new PaymentEntity(
                orderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new BigDecimal("100.00"),
                "BTN"
        );

        PaymentEntity second = new PaymentEntity(
                orderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new BigDecimal("100.00"),
                "BTN"
        );

        paymentRepository.saveAndFlush(first);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(second)
        );
    }

    @Test
    void shouldFindPaymentByOrderId() {

        UUID orderId = UUID.randomUUID();

        PaymentEntity payment = new PaymentEntity(
                orderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new BigDecimal("100.00"),
                "BTN"
        );

        paymentRepository.saveAndFlush(payment);

        assertTrue(paymentRepository.findByOrderId(orderId).isPresent());
        assertTrue(
                paymentRepository.findByOrderId(UUID.randomUUID()).isEmpty()
        );
    }
}
