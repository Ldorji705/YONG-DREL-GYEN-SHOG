
package com.yongdrel.claim.adapter.in.web;

import com.yongdrel.claim.adapter.in.web.dto.ClaimResponse;
import com.yongdrel.claim.adapter.in.web.dto.CreateClaimRequest;
import com.yongdrel.claim.adapter.in.web.dto.FulfillClaimRequest;
import com.yongdrel.claim.adapter.in.web.dto.RejectClaimRequest;
import com.yongdrel.claim.application.service.PrizeClaimApplicationService;
import com.yongdrel.claim.domain.model.PrizeClaim;
import com.yongdrel.claim.domain.port.PrizeClaimRepository;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/claims")
public class PrizeClaimController {

    private final PrizeClaimApplicationService claimService;
    private final PrizeClaimRepository claimRepository;

    public PrizeClaimController(
            PrizeClaimApplicationService claimService,
            PrizeClaimRepository claimRepository
    ) {
        this.claimService = claimService;
        this.claimRepository = claimRepository;
    }

    // Submit a new claim or return an existing idempotent result.
    @PostMapping
    public ResponseEntity<ClaimResponse> submitClaim(
            @Valid @RequestBody CreateClaimRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        var existing = claimRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        );

        if (existing.isPresent()) {
            PrizeClaim previousClaim = existing.get();

            if (!previousClaim.winnerId().equals(request.winnerId())
                    || !previousClaim.prizeId().equals(request.prizeId())) {
                throw new IllegalStateException(
                        "Idempotency key has already been used for another claim"
                );
            }

            return ResponseEntity.ok(
                    ClaimResponse.from(previousClaim)
            );
        }

        String accessToken = jwt.getTokenValue();

        PrizeClaim claim = claimService.submitClaim(
                request.winnerId(),
                request.prizeId(),
                userId,
                idempotencyKey,
                accessToken
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ClaimResponse.from(claim));
    }

    // Retrieve the authenticated user's claims.
    @GetMapping("/me")
    public ResponseEntity<List<ClaimResponse>> getMyClaims(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<ClaimResponse> claims = claimService
                .getMyClaims(jwt.getSubject())
                .stream()
                .map(ClaimResponse::from)
                .toList();

        return ResponseEntity.ok(claims);
    }

    // Retrieve one claim. Users can only view their own claims.
    // Administrators can view any claim.
    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponse> getClaim(
            @PathVariable UUID claimId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        PrizeClaim claim = claimService.getClaim(claimId);

        boolean isAdmin = "ADMIN".equals(
                jwt.getClaimAsString("role")
        );

        if (!isAdmin && !claim.userId().equals(jwt.getSubject())) {
            throw new AccessDeniedException(
                    "You are not allowed to view this claim"
            );
        }

        return ResponseEntity.ok(ClaimResponse.from(claim));
    }

    // Retrieve all claims. Admin only.
    @GetMapping
    public ResponseEntity<List<ClaimResponse>> getAllClaims() {
        List<ClaimResponse> claims = claimService
                .getAllClaims()
                .stream()
                .map(ClaimResponse::from)
                .toList();

        return ResponseEntity.ok(claims);
    }

    // Move a submitted claim into review. Admin only.
    @PatchMapping("/{claimId}/review")
    public ResponseEntity<ClaimResponse> startReview(
            @PathVariable UUID claimId
    ) {
        return ResponseEntity.ok(
                ClaimResponse.from(claimService.startReview(claimId))
        );
    }

    // Approve a claim. Admin only.
    @PatchMapping("/{claimId}/approve")
    public ResponseEntity<ClaimResponse> approveClaim(
            @PathVariable UUID claimId
    ) {
        return ResponseEntity.ok(
                ClaimResponse.from(claimService.approveClaim(claimId))
        );
    }

    // Reject a claim. Admin only.
    @PatchMapping("/{claimId}/reject")
    public ResponseEntity<ClaimResponse> rejectClaim(
            @PathVariable UUID claimId,
            @Valid @RequestBody RejectClaimRequest request
    ) {
        return ResponseEntity.ok(
                ClaimResponse.from(
                        claimService.rejectClaim(
                                claimId,
                                request.rejectionReason()
                        )
                )
        );
    }

    // Mark an approved claim as fulfilled. Admin only.
    @PatchMapping("/{claimId}/fulfill")
    public ResponseEntity<ClaimResponse> fulfillClaim(
            @PathVariable UUID claimId,
            @Valid @RequestBody FulfillClaimRequest request
    ) {
        return ResponseEntity.ok(
                ClaimResponse.from(
                        claimService.fulfillClaim(
                                claimId,
                                request.fulfillmentReference()
                        )
                )
        );
    }
}
