package com.yongdrel.scheduler_ttl_service.application.port;
import java.time.Instant;import java.util.*;import java.util.UUID;
public interface ExpiryPorts{
 interface Orders{List<Record> expired(Instant before);void expire(UUID id,UUID tenant);}
 interface Inventory{List<Record> expired(Instant before);void release(UUID id,UUID tenant);}
 interface Events{List<Record> closing(Instant before);void close(UUID id);}
 record Record(UUID id,UUID tenantId,Instant expiresAt){}
}
