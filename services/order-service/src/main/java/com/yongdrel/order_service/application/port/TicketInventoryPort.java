package com.yongdrel.order_service.application.port;
import java.util.UUID;
public interface TicketInventoryPort {
 Reservation reservation(UUID reservationId,UUID tenantId,UUID userId);
 void release(UUID reservationId,UUID tenantId);
 record Reservation(UUID reservationId,UUID tenantId,UUID eventId,UUID userId,int quantity,java.math.BigDecimal amount,String currency,boolean active){}
}
