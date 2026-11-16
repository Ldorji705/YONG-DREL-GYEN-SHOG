package com.yongdrel.order_service.application.port;
import java.util.UUID;
public interface PaymentTrustPort {
 boolean verifyProof(UUID orderId,UUID tenantId,String proofReference);
 boolean approved(UUID orderId,UUID tenantId);
}
