package com.yongdrel.bulk_verification_service.application.port;
import java.util.UUID; public interface PaymentLookupPort{PaymentMatch find(String reference,UUID tenant);record PaymentMatch(UUID paymentId,UUID orderId,UUID tenantId,boolean eligibleForReview,boolean sourceAvailable){}}
