package com.yongdrel.wallet_service.adapter.in.web;

import com.yongdrel.wallet_service.adapter.in.web.dto.CreditRequest;
import com.yongdrel.wallet_service.adapter.in.web.dto.DebitRequest;
import com.yongdrel.wallet_service.adapter.in.web.dto.OperationResponse;
import com.yongdrel.wallet_service.adapter.in.web.dto.WalletResponse;
import com.yongdrel.wallet_service.application.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No PUT/PATCH/DELETE: balances only change through credit/debit operations. */
@RestController
@Validated
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** The caller's wallet (created with balance 0 on first use). */
    @GetMapping("/me")
    public WalletResponse me(@AuthenticationPrincipal Jwt jwt) {
        return WalletResponse.from(walletService.getOrCreate(jwt.getSubject()));
    }

    /** User spends from their own wallet. 201 = applied; 200 = same key already applied. */
    @PostMapping("/debit")
    public ResponseEntity<OperationResponse> debit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody DebitRequest request
    ) {
        return respond(walletService.debit(jwt.getSubject(), idempotencyKey,
                request.amount(), request.description()));
    }

    /** Internal services (X-Internal-Api-Key) or ADMIN only; see SecurityConfig. */
    @PostMapping("/credit")
    public ResponseEntity<OperationResponse> credit(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreditRequest request
    ) {
        return respond(walletService.credit(request.ownerId(), idempotencyKey,
                request.amount(), request.description()));
    }

    private static ResponseEntity<OperationResponse> respond(WalletService.ApplyResult result) {
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(OperationResponse.from(result.operation()));
    }
}
