package com.yongdrel.payment_service.application.port;
import java.util.UUID;
public interface OrderPaymentStatePort{void confirmed(UUID orderId,UUID tenantId,UUID paymentId);void failed(UUID orderId,UUID tenantId);}
