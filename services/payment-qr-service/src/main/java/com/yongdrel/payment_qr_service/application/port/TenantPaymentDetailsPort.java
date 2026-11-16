package com.yongdrel.payment_qr_service.application.port;
import java.util.UUID;
public interface TenantPaymentDetailsPort { PaymentDetails get(UUID tenantId,UUID organizerId); record PaymentDetails(UUID tenantId,UUID organizerId,String destinationName,String accountReference,String instructions){} }
