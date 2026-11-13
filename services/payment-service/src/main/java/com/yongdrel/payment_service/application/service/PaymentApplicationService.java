package com.yongdrel.payment_service.application.service;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentEntity;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentRepository;
import com.yongdrel.payment_service.application.port.OrderServicePort;
import com.yongdrel.payment_service.application.port.TrustedOrder;
import com.yongdrel.payment_service.dto.PaymentResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentApplicationService {

    private final PaymentRepository paymentRepository;
    private final OrderServicePort orderServicePort;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            OrderServicePort orderServicePort
    ) {
        this.paymentRepository = paymentRepository;
        this.orderServicePort = orderServicePort;
    }

    @Transactional
    public PaymentResponse initiatePayment(
            UUID orderId,
            Long authenticatedUserId
    ) {
        if (orderId == null || authenticatedUserId == null
                || authenticatedUserId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid payment request"
            );
        }

        Optional<PaymentEntity> existing =
                paymentRepository.findByOrderId(orderId);

        if (existing.isPresent()) {
            PaymentEntity payment = existing.get();

            if (!payment.getUserId().equals(authenticatedUserId)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                );
            }

            return PaymentResponse.from(payment);
        }

        TrustedOrder order = orderServicePort.getOrder(orderId);

        if (order == null
                || order.orderId() == null
                || !orderId.equals(order.orderId())
                || order.lotteryEventId() == null
                || order.userId() == null
                || order.quantity() == null
                || order.amount() == null
                || order.currency() == null
                || order.status() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Invalid order information"
            );
        }

        if (!order.userId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Order not found"
            );
        }

        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order is not awaiting payment"
            );
        }

        if (order.quantity() < 1
                || order.quantity() > 100
                || order.amount().compareTo(BigDecimal.ZERO) <= 0
                || order.amount().scale() > 2
                || !order.currency().matches("[A-Z]{3}")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Invalid order pricing or quantity"
            );
        }

        PaymentEntity payment = new PaymentEntity(
                order.orderId(),
                order.lotteryEventId(),
                order.userId(),
                order.quantity(),
                order.amount(),
                order.currency()
        );

        PaymentEntity saved = paymentRepository.save(payment);

        return PaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(
            UUID paymentId,
            Long authenticatedUserId
    ) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        if (!payment.getUserId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment not found"
            );
        }

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(
            UUID orderId,
            Long authenticatedUserId
    ) {
        PaymentEntity payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        if (!payment.getUserId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payment not found"
            );
        }

        return PaymentResponse.from(payment);
    }
}
