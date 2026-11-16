package com.yongdrel.ticket_inventory_service.application.port;
import java.util.UUID;
public interface EventOwnershipPort { boolean verify(UUID eventId, UUID tenantId); }
