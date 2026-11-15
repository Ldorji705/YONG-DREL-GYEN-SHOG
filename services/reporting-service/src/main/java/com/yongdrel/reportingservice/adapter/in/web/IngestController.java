package com.yongdrel.reportingservice.adapter.in.web;

import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.EventIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.IngestResponse;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.OrderIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.PayoutIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.PrizeIngest;
import com.yongdrel.reportingservice.application.port.in.IngestUseCase;
import com.yongdrel.reportingservice.domain.exception.InvalidProjectionException;
import com.yongdrel.reportingservice.domain.model.UpsertOutcome;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owning services push their current state (idempotent PUT). 201 = new, 200 = updated or identical repeat,
 * 409 = older/conflicting version or changed immutable reference, 422 = unknown/foreign event or prize.
 */
@RestController
@RequestMapping("/api/reports/ingest")
public class IngestController {

    private final IngestUseCase ingest;

    public IngestController(IngestUseCase ingest) {
        this.ingest = ingest;
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<IngestResponse> event(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id,
                                                @Valid @RequestBody EventIngest body) {
        return respond("EVENT", id, ingest.upsertEvent(body.toProjection(pathId(id, body.id())), JwtCallerMapper.toCaller(jwt)));
    }

    @PutMapping("/orders/{id}")
    public ResponseEntity<IngestResponse> order(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id,
                                                @Valid @RequestBody OrderIngest body) {
        return respond("ORDER", id, ingest.upsertOrder(body.toProjection(pathId(id, body.id())), JwtCallerMapper.toCaller(jwt)));
    }

    @PutMapping("/prizes/{id}")
    public ResponseEntity<IngestResponse> prize(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id,
                                                @Valid @RequestBody PrizeIngest body) {
        return respond("PRIZE", id, ingest.upsertPrize(body.toProjection(pathId(id, body.id())), JwtCallerMapper.toCaller(jwt)));
    }

    @PutMapping("/payouts/{id}")
    public ResponseEntity<IngestResponse> payout(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id,
                                                 @Valid @RequestBody PayoutIngest body) {
        return respond("PAYOUT", id, ingest.upsertPayout(body.toProjection(pathId(id, body.id())), JwtCallerMapper.toCaller(jwt)));
    }

    private static Long pathId(Long pathId, Long bodyId) {
        if (bodyId != null && !bodyId.equals(pathId)) {
            throw new InvalidProjectionException("Body id " + bodyId + " does not match path id " + pathId);
        }
        return pathId;
    }

    private static ResponseEntity<IngestResponse> respond(String kind, Long id, UpsertOutcome outcome) {
        return ResponseEntity.status(outcome == UpsertOutcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new IngestResponse(kind, id, outcome.name()));
    }
}
