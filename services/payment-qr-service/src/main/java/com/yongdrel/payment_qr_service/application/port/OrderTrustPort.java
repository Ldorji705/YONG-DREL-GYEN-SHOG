package com.yongdrel.payment_qr_service.application.port;
import java.math.BigDecimal; import java.util.UUID;
public interface OrderTrustPort { OrderDetails get(UUID orderId,UUID tenantId); record OrderDetails(UUID orderId,UUID tenantId,BigDecimal amount,String currency,String status,String reference){} }
