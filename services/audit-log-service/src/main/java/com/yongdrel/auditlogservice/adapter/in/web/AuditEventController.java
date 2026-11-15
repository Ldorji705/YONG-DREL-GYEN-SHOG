package com.yongdrel.auditlogservice.adapter.in.web;

import com.yongdrel.auditlogservice.adapter.in.web.dto.AuditEventResponse;
import com.yongdrel.auditlogservice.adapter.in.web.dto.PageResponse;
import com.yongdrel.auditlogservice.adapter.in.web.dto.RecordAuditEventRequest;
import com.yongdrel.auditlogservice.application.model.AuditSearchCriteria;
import com.yongdrel.auditlogservice.application.port.in.AuditQueryUseCase;
import com.yongdrel.auditlogservice.application.port.in.RecordAuditEventUseCase;
import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.TargetType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Append-only: there are no PUT, PATCH or DELETE endpoints (405). */
@RestController
@RequestMapping("/api/audit")
public class AuditEventController {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final RecordAuditEventUseCase recorder;
    private final AuditQueryUseCase queries;

    public AuditEventController(RecordAuditEventUseCase recorder, AuditQueryUseCase queries) {
        this.recorder = recorder;
        this.queries = queries;
    }

    /** correlationId: body, else X-Correlation-Id header, else a new UUID. Echoed in the response header. */
    @PostMapping("/events")
    public ResponseEntity<AuditEventResponse> record(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String headerCorrelationId,
            @Valid @RequestBody RecordAuditEventRequest request) {
        String correlationId = request.correlationId() != null ? request.correlationId()
                : headerCorrelationId != null && !headerCorrelationId.isBlank() ? headerCorrelationId.trim()
                : UUID.randomUUID().toString();
        AuditEvent saved = recorder.record(request.toDraft(correlationId), JwtCallerMapper.toCaller(jwt));
        return ResponseEntity.created(URI.create("/api/audit/events/" + saved.id()))
                .header(CORRELATION_HEADER, saved.correlationId())
                .body(AuditEventResponse.from(saved));
    }

    /** Restricted search, newest first. Organizers see their tenant, students their own actions. */
    @GetMapping("/events")
    public PageResponse<AuditEventResponse> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Positive Long tenantId,
            @RequestParam(required = false) @Positive Long actorId,
            @RequestParam(required = false) ActorType actorType,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$") String action,
            @RequestParam(required = false) Outcome outcome,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z0-9._:-]{1,64}$") String targetId,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Za-z0-9._-]{8,64}$") String correlationId,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z][a-z0-9-]{2,49}$") String sourceService,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var criteria = new AuditSearchCriteria(tenantId, actorId, actorType, action, outcome, targetType, targetId,
                correlationId, sourceService, from, to);
        return PageResponse.from(queries.search(criteria, page, size, JwtCallerMapper.toCaller(jwt)),
                AuditEventResponse::from);
    }

    @GetMapping("/events/{id}")
    public AuditEventResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        return AuditEventResponse.from(queries.get(id, JwtCallerMapper.toCaller(jwt)));
    }

    /** Every visible event of one business transaction, oldest first. */
    @GetMapping("/correlations/{correlationId}")
    public List<AuditEventResponse> timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable String correlationId) {
        return queries.timeline(correlationId, JwtCallerMapper.toCaller(jwt)).stream()
                .map(AuditEventResponse::from).toList();
    }
}
