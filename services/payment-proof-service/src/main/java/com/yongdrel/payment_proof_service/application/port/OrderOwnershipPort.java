package com.yongdrel.payment_proof_service.application.port;
import java.util.UUID; public interface OrderOwnershipPort{boolean owns(UUID orderId,UUID userId,UUID tenantId);}
