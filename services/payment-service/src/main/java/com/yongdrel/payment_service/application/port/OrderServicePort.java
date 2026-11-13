package com.yongdrel.payment_service.application.port;

import java.util.UUID;

public interface OrderServicePort {

    TrustedOrder getOrder(UUID orderId);
}
