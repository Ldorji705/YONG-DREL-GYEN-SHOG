package com.yongdrel.payment_proof_service.adapter.in.web;
import com.yongdrel.payment_proof_service.adapter.out.persistence.PaymentProofEntity; import com.yongdrel.payment_proof_service.application.service.PaymentProofService; import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.UUID;
@RestController @RequestMapping("/internal/payment-proofs") public class InternalPaymentProofController{
 private final PaymentProofService service; private final byte[] key;
 public InternalPaymentProofController(PaymentProofService s,@Value("${app.internal.api-key}")String configured){if(configured==null||configured.isBlank())throw new IllegalStateException("Internal proof key required");service=s;key=configured.getBytes(StandardCharsets.UTF_8);}
 @GetMapping("/{id}") public Metadata get(@PathVariable UUID id,@RequestHeader(value="X-Internal-API-Key",required=false)String provided){if(provided==null||!MessageDigest.isEqual(key,provided.getBytes(StandardCharsets.UTF_8)))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid internal credentials");PaymentProofEntity p=service.verifyMetadata(id);return new Metadata(p.getId(),p.getOrderId(),p.getUserId(),p.getTenantId(),p.getStatus().name(),p.getChecksum());}
 public record Metadata(UUID id,UUID orderId,UUID userId,UUID tenantId,String status,String checksum){}
}
