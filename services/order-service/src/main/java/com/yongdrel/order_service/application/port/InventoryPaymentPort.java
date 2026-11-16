package com.yongdrel.order_service.application.port;
import java.util.UUID;
public interface InventoryPaymentPort{void confirm(UUID reservationId,UUID tenantId,UUID paymentId);void release(UUID reservationId,UUID tenantId);}
