package com.yongdrel.transaction_ledger_service.adapter.in.web;

import com.yongdrel.transaction_ledger_service.adapter.in.web.dto.CreateLedgerEntryRequest;
import com.yongdrel.transaction_ledger_service.adapter.in.web.dto.LedgerEntryResponse;
import com.yongdrel.transaction_ledger_service.adapter.in.web.dto.PageResponse;
import com.yongdrel.transaction_ledger_service.application.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Append-only API: there is deliberately no PUT, PATCH or DELETE endpoint.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /**
     * Internal: record a financial movement.
     * 201 Created = new entry; 200 OK = same referenceId already recorded (safe retry).
     */
    @PostMapping("/entries")
    public ResponseEntity<LedgerEntryResponse> record(
            @Valid @RequestBody CreateLedgerEntryRequest request
    ) {
        LedgerService.RecordResult result = ledgerService.record(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(LedgerEntryResponse.from(result.entry()));
    }

    /** The caller's own history; the account comes from the JWT, never from the request. */
    @GetMapping("/me")
    public PageResponse<LedgerEntryResponse> myEntries(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(
                ledgerService.findForAccount(jwt.getSubject(), page, size),
                LedgerEntryResponse::from
        );
    }
}
