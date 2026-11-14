package com.yongdrel.payment_service.adapter.out.order;

import com.yongdrel.payment_service.application.port.OrderServicePort;
import com.yongdrel.payment_service.application.port.TrustedOrder;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
@Profile("!test")
public class OrderServiceAdapter implements OrderServicePort {

    @Override
    public TrustedOrder getOrder(UUID orderId) {

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Order Service integration is not available yet"
        );
    }
}
