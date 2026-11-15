package com.yongdrel.fraudriskservice.adapter.in.web;

import com.yongdrel.fraudriskservice.adapter.in.web.dto.PageResponse;
import com.yongdrel.fraudriskservice.adapter.in.web.dto.ReviewFlagRequest;
import com.yongdrel.fraudriskservice.adapter.in.web.dto.RiskFlagResponse;
import com.yongdrel.fraudriskservice.application.model.FlagSearchCriteria;
import com.yongdrel.fraudriskservice.application.port.in.RiskFlagUseCase;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskLevel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fraud/flags")
public class RiskFlagController {

    private final RiskFlagUseCase useCase;

    public RiskFlagController(RiskFlagUseCase useCase) {
        this.useCase = useCase;
    }

    /** Manual-review queue. Organizers are always scoped to their own tenant; newest first. */
    @GetMapping
    public PageResponse<RiskFlagResponse> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Positive Long tenantId,
            @RequestParam(required = false) @Positive Long eventId,
            @RequestParam(required = false) @Positive Long orderId,
            @RequestParam(required = false) @Positive Long buyerId,
            @RequestParam(required = false) FlagStatus status,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var criteria = new FlagSearchCriteria(tenantId, eventId, orderId, buyerId, status, riskLevel);
        return PageResponse.from(useCase.search(criteria, page, size, JwtActorMapper.toActor(jwt)),
                RiskFlagResponse::from);
    }

    @GetMapping("/{id}")
    public RiskFlagResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        return RiskFlagResponse.from(useCase.get(id, JwtActorMapper.toActor(jwt)));
    }

    @PatchMapping("/{id}/review")
    public RiskFlagResponse review(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id,
                                   @Valid @RequestBody ReviewFlagRequest req) {
        return RiskFlagResponse.from(useCase.review(id, req.status(), req.note(), JwtActorMapper.toActor(jwt)));
    }
}
