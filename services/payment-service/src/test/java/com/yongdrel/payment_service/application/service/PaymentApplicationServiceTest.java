package com.yongdrel.payment_service.application.service;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentEntity;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentRepository;
import com.yongdrel.payment_service.application.port.OrderServicePort;
import com.yongdrel.payment_service.application.port.TrustedOrder;
import com.yongdrel.payment_service.domain.model.PaymentStatus;
import com.yongdrel.payment_service.dto.PaymentResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderServicePort orderServicePort;

    @InjectMocks
    private PaymentApplicationService paymentService;

    private UUID orderId;
    private UUID eventId;
    private Long userId;
    private TrustedOrder validOrder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        userId = 101L;

        validOrder = new TrustedOrder(
                orderId,
                eventId,
                userId,
                2,
                new BigDecimal("200.00"),
                "BTN",
                "PENDING_PAYMENT"
        );
    }

    @Test
    void shouldInitiatePaymentSuccessfully() {

        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        when(orderServicePort.getOrder(orderId))
                .thenReturn(validOrder);

        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response =
                paymentService.initiatePayment(orderId, userId);

        assertNotNull(response.id());
        assertEquals(orderId, response.orderId());
        assertEquals(userId, response.userId());
        assertEquals(new BigDecimal("200.00"), response.amount());
        assertEquals("BTN", response.currency());
        assertEquals(PaymentStatus.PENDING, response.status());

        verify(paymentRepository).save(any(PaymentEntity.class));
    }

    @Test
    void shouldReturnExistingPaymentWithoutCreatingDuplicate() {

        PaymentEntity existing = new PaymentEntity(
                orderId, eventId, userId, 2,
                new BigDecimal("200.00"), "BTN"
        );

        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(existing));

        PaymentResponse response =
                paymentService.initiatePayment(orderId, userId);

        assertEquals(existing.getId(), response.id());

        verify(paymentRepository, never())
                .save(any(PaymentEntity.class));

        verifyNoInteractions(orderServicePort);
    }

    @Test
    void shouldRejectPaymentForAnotherUser() {

        TrustedOrder anotherUsersOrder = new TrustedOrder(
                orderId, eventId, 999L, 2,
                new BigDecimal("200.00"), "BTN",
                "PENDING_PAYMENT"
        );

        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        when(orderServicePort.getOrder(orderId))
                .thenReturn(anotherUsersOrder);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> paymentService.initiatePayment(orderId, userId)
                );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verify(paymentRepository, never())
                .save(any(PaymentEntity.class));
    }

    @Test
    void shouldRejectOrderNotAwaitingPayment() {

        TrustedOrder completedOrder = new TrustedOrder(
                orderId, eventId, userId, 2,
                new BigDecimal("200.00"), "BTN",
                "COMPLETED"
        );

        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        when(orderServicePort.getOrder(orderId))
                .thenReturn(completedOrder);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> paymentService.initiatePayment(orderId, userId)
                );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(paymentRepository, never())
                .save(any(PaymentEntity.class));
    }

    @Test
    void shouldRejectInvalidAmount() {

        TrustedOrder invalidOrder = new TrustedOrder(
                orderId, eventId, userId, 2,
                BigDecimal.ZERO, "BTN",
                "PENDING_PAYMENT"
        );

        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        when(orderServicePort.getOrder(orderId))
                .thenReturn(invalidOrder);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> paymentService.initiatePayment(orderId, userId)
                );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());

        verify(paymentRepository, never())
                .save(any(PaymentEntity.class));
    }

    @Test
    void shouldRejectInvalidQuantity() {

        TrustedOrder invalidOrder = new TrustedOrder(
                orderId, eventId, userId, 101,
                new BigDecimal("200.00"), "BTN",
                "PENDING_PAYMENT"
        );

        when(paymentRepository.findByOrderId(orderId))
                .thenReturn(Optional.empty());

        when(orderServicePort.getOrder(orderId))
                .thenReturn(invalidOrder);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> paymentService.initiatePayment(orderId, userId)
                );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());

        verify(paymentRepository, never())
                .save(any(PaymentEntity.class));
    }

    @Test
    void shouldReturnPaymentToItsOwner() {

        UUID paymentId = UUID.randomUUID();

        PaymentEntity payment = new PaymentEntity(
                orderId, eventId, userId, 2,
                new BigDecimal("200.00"), "BTN"
        );

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        PaymentResponse response =
                paymentService.getPayment(paymentId, userId);

        assertEquals(payment.getId(), response.id());
        assertEquals(userId, response.userId());
    }

    @Test
    void shouldHidePaymentFromAnotherUser() {

        UUID paymentId = UUID.randomUUID();

        PaymentEntity payment = new PaymentEntity(
                orderId, eventId, 999L, 2,
                new BigDecimal("200.00"), "BTN"
        );

        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(payment));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> paymentService.getPayment(paymentId, userId)
                );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldRejectInvalidPaymentRequest() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> paymentService.initiatePayment(null, userId)
                );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(paymentRepository, orderServicePort);
    }
}
