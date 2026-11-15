package com.yongdrel.fraudriskservice.adapter.in.web;

import com.yongdrel.fraudriskservice.adapter.in.web.dto.CreateRuleRequest;
import com.yongdrel.fraudriskservice.adapter.in.web.dto.RiskRuleResponse;
import com.yongdrel.fraudriskservice.adapter.in.web.dto.UpdateRuleRequest;
import com.yongdrel.fraudriskservice.application.port.in.RiskRuleUseCase;
import com.yongdrel.fraudriskservice.domain.model.RiskRule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/fraud/rules")
public class RiskRuleController {

    private final RiskRuleUseCase useCase;

    public RiskRuleController(RiskRuleUseCase useCase) {
        this.useCase = useCase;
    }

    /** Without tenantId: platform defaults. With tenantId: that tenant's overrides only. */
    @GetMapping
    public List<RiskRuleResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) @Positive Long tenantId) {
        return useCase.list(tenantId, JwtActorMapper.toActor(jwt)).stream().map(RiskRuleResponse::from).toList();
    }

    /** The rule set actually applied to a tenant (defaults merged with overrides). */
    @GetMapping("/effective")
    public List<RiskRuleResponse> effective(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) @Positive Long tenantId) {
        return useCase.effective(tenantId, JwtActorMapper.toActor(jwt)).stream()
                .map(RiskRuleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RiskRuleResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        return RiskRuleResponse.from(useCase.get(id, JwtActorMapper.toActor(jwt)));
    }

    @PostMapping
    public ResponseEntity<RiskRuleResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CreateRuleRequest req) {
        RiskRule rule = useCase.create(req.tenantId(), req.ruleType(), req.settings(), JwtActorMapper.toActor(jwt));
        return ResponseEntity.created(URI.create("/api/fraud/rules/" + rule.getId()))
                .body(RiskRuleResponse.from(rule));
    }

    @PutMapping("/{id}")
    public RiskRuleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id,
                                   @Valid @RequestBody UpdateRuleRequest req) {
        return RiskRuleResponse.from(useCase.update(id, req.settings(), JwtActorMapper.toActor(jwt)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        useCase.delete(id, JwtActorMapper.toActor(jwt));
        return ResponseEntity.noContent().build();
    }
}
