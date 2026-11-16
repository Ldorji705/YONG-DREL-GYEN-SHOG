package com.yongdrel.ticket_inventory_service.application.port;
import java.util.UUID;
public interface PaymentVerificationPort { boolean verify(UUID paymentId, UUID reservationId, UUID tenantId); }
