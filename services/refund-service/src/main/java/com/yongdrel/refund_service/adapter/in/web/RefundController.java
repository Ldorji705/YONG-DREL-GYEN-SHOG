package com.yongdrel.refund_service.adapter.in.web;

import com.yongdrel.refund_service.adapter.in.web.dto.CreateRefundRequest;
import com.yongdrel.refund_service.adapter.in.web.dto.RefundResponse;
import com.yongdrel.refund_service.application.service.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** No PUT/PATCH/DELETE: refunds are never edited or removed by clients. */
@RestController
@Validated
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    /** 201 = new refund; 200 = same Idempotency-Key already handled (body shows its status). */
    @PostMapping
    public ResponseEntity<RefundResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request
    ) {
        RefundService.CreateResult result = refundService.create(
                request, jwt.getSubject(), jwt.getTokenValue(), idempotencyKey);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(RefundResponse.from(result.refund()));
    }

    @GetMapping("/{id}")
    public RefundResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return RefundResponse.from(refundService.get(id, jwt.getSubject(), isAdmin(jwt)));
    }

    /** Retries a FAILED refund (provider and ledger steps). */
    @PostMapping("/{id}/retry")
    public RefundResponse retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return RefundResponse.from(refundService.retry(id, jwt.getSubject(), isAdmin(jwt)));
    }

    private static boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }
}
