package com.yongdrel.ticket_inventory_service.adapter.in.web;
import com.yongdrel.ticket_inventory_service.adapter.out.persistence.ReservationEntity;
import com.yongdrel.ticket_inventory_service.application.service.TicketInventoryApplicationService;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.UUID;
@RestController @RequestMapping("/internal/ticket-inventory") public class InternalInventoryController{
 private final TicketInventoryApplicationService service;private final byte[] key;
 public InternalInventoryController(TicketInventoryApplicationService s,@Value("${app.internal.api-key}")String k){if(k==null||k.isBlank())throw new IllegalStateException("Inventory key required");service=s;key=k.getBytes(StandardCharsets.UTF_8);}
 @GetMapping("/reservations/{id}") public Reservation get(@PathVariable UUID id,@RequestHeader("X-Internal-API-Key")String provided,@RequestHeader("X-Tenant-Id")UUID tenant,@RequestHeader("X-User-Id")UUID user){auth(provided);return Reservation.of(service.internalReservation(id,tenant,user));}
 @PostMapping("/reservations/{id}/release")@ResponseStatus(HttpStatus.NO_CONTENT)public void release(@PathVariable UUID id,@RequestHeader("X-Internal-API-Key")String provided,@RequestHeader("X-Tenant-Id")UUID tenant){auth(provided);service.release(id,tenant);}
 @GetMapping("/reservations/expired") public java.util.List<Expired> expired(@RequestParam java.time.Instant before,@RequestParam(defaultValue="100")int limit,@RequestHeader("X-Internal-API-Key")String provided){auth(provided);if(limit<1||limit>100)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid limit");return service.expired(before).stream().limit(limit).map(r->new Expired(r.getId(),r.getTenantId(),r.getExpiresAt())).toList();}
 @PostMapping("/reservations/{id}/confirm")@ResponseStatus(HttpStatus.NO_CONTENT)public void confirm(@PathVariable UUID id,@RequestHeader("X-Internal-API-Key")String provided,@RequestHeader("X-Tenant-Id")UUID tenant,@RequestHeader("X-Payment-Id")UUID payment){auth(provided);service.confirmInternal(id,tenant,payment);}
 private void auth(String p){if(p==null||!MessageDigest.isEqual(key,p.getBytes(StandardCharsets.UTF_8)))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid internal credentials");}
 record Reservation(UUID reservationId,UUID tenantId,UUID eventId,UUID userId,int quantity,java.math.BigDecimal amount,String currency,boolean active){static Reservation of(ReservationEntity r){return new Reservation(r.getId(),r.getTenantId(),r.getEventId(),r.getUserId(),r.getQuantity(),r.getAmount(),r.getCurrency(),r.getStatus().name().equals("RESERVED")&&r.getExpiresAt().isAfter(java.time.Instant.now()));}}
 record Expired(UUID id,UUID tenantId,java.time.Instant expiresAt){}
}
