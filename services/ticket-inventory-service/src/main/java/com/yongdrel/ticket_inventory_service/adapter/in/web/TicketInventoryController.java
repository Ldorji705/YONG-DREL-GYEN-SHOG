package com.yongdrel.ticket_inventory_service.adapter.in.web;
import com.yongdrel.ticket_inventory_service.adapter.out.persistence.ReservationEntity;
import com.yongdrel.ticket_inventory_service.application.service.TicketInventoryApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/ticket-inventory")
public class TicketInventoryController {
 private final TicketInventoryApplicationService service;
 public TicketInventoryController(TicketInventoryApplicationService service){this.service=service;}
 @GetMapping("/events/{eventId}/availability")
 public TicketInventoryApplicationService.Availability availability(@PathVariable UUID eventId, Authentication a) {
  return service.availability(eventId, claim(a,"tenant_id"));
 }
 @PostMapping("/reservations")
 public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest r, Authentication a) {
  ReservationEntity x=service.reserve(r.eventId(),claim(a,"tenant_id"),claim(a,"sub"),r.quantity(),r.idempotencyKey());
  return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.of(x));
 }
 @PostMapping("/reservations/{id}/confirm")
 public ReservationResponse confirm(@PathVariable UUID id,@Valid @RequestBody ConfirmRequest r,Authentication a){return ReservationResponse.of(service.confirm(id,claim(a,"tenant_id"),r.paymentId()));}
 @PostMapping("/reservations/{id}/release")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 public void release(@PathVariable UUID id,Authentication a){service.release(id,claim(a,"tenant_id"));}
 private static UUID claim(Authentication a,String name){if(!(a.getPrincipal() instanceof Jwt jwt)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"Trusted JWT required"); String v=jwt.getClaimAsString(name); try{return UUID.fromString(v);}catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Required UUID claim missing");}}
 public record ReserveRequest(@NotNull UUID eventId,@Min(1) int quantity,@NotBlank String idempotencyKey){}
 public record ConfirmRequest(@NotNull UUID paymentId){}
 public record ReservationResponse(UUID id,UUID eventId,int quantity,String status,java.time.Instant expiresAt){static ReservationResponse of(ReservationEntity x){return new ReservationResponse(x.getId(),x.getEventId(),x.getQuantity(),x.getStatus().name(),x.getExpiresAt());}}
}
