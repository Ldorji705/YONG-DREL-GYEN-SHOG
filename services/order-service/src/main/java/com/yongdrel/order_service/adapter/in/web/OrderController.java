package com.yongdrel.order_service.adapter.in.web;
import com.yongdrel.order_service.adapter.out.persistence.OrderEntity; import com.yongdrel.order_service.application.service.OrderApplicationService;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.security.oauth2.jwt.Jwt; import org.springframework.web.bind.annotation.*; import java.util.*; import java.time.Instant;
@RestController @RequestMapping("/api/orders") public class OrderController{
 private final OrderApplicationService service; public OrderController(OrderApplicationService s){service=s;}
 @PostMapping public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest r,Authentication a){OrderEntity o=service.create(claim(a,"sub"),claim(a,"tenant_id"),r.reservationId());return ResponseEntity.status(HttpStatus.CREATED).body(Response.of(o));}
 @GetMapping("/me") public List<Response> own(Authentication a){return service.own(claim(a,"sub"),claim(a,"tenant_id")).stream().map(Response::of).toList();}
 @GetMapping("/tenant") public List<Response> tenant(Authentication a){if(!a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ORGANIZER")))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Organizer role required");return service.tenant(claim(a,"tenant_id")).stream().map(Response::of).toList();}
 @PostMapping("/{id}/payment-proof") public Response submit(@PathVariable UUID id,@Valid @RequestBody ProofRequest r,Authentication a){return Response.of(service.submitProof(id,claim(a,"tenant_id"),r.proofReference()));}
 @PostMapping("/{id}/confirm") public Response confirm(@PathVariable UUID id,Authentication a){return Response.of(service.confirm(id,claim(a,"tenant_id")));}
 @PostMapping("/{id}/fail") @ResponseStatus(HttpStatus.NO_CONTENT) public void fail(@PathVariable UUID id,Authentication a){service.fail(id,claim(a,"tenant_id"));}
 private static UUID claim(Authentication a,String n){if(!(a.getPrincipal() instanceof Jwt j))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"Trusted JWT required");try{return UUID.fromString(j.getClaimAsString(n));}catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Required UUID claim missing");}}
 public record CreateRequest(UUID reservationId){} public record ProofRequest(@NotBlank String proofReference){}
 public record Response(UUID id,UUID userId,UUID tenantId,UUID eventId,UUID reservationId,String status,Instant createdAt){static Response of(OrderEntity o){return new Response(o.getId(),o.getUserId(),o.getTenantId(),o.getEventId(),o.getReservationId(),o.getStatus().name(),o.getCreatedAt());}}
}
