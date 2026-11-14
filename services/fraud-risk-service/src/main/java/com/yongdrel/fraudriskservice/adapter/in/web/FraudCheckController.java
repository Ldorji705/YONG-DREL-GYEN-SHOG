package com.yongdrel.fraudriskservice.adapter.in.web;

import com.yongdrel.fraudriskservice.adapter.in.web.dto.BuyerRiskCheckResponse;
import com.yongdrel.fraudriskservice.adapter.in.web.dto.CreateRiskCheckRequest;
import com.yongdrel.fraudriskservice.adapter.in.web.dto.RiskCheckResponse;
import com.yongdrel.fraudriskservice.application.port.in.AssessTransactionCommand;
import com.yongdrel.fraudriskservice.application.port.in.AssessTransactionUseCase;
import com.yongdrel.fraudriskservice.application.port.in.AssessmentResult;
import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/fraud/checks")
public class FraudCheckController {

    private final AssessTransactionUseCase useCase;

    public FraudCheckController(AssessTransactionUseCase useCase) {
        this.useCase = useCase;
    }

    /** 201 for a new check, 200 when the identical submission was already checked (idempotent replay). */
    @PostMapping
    public ResponseEntity<Object> assess(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody CreateRiskCheckRequest req) {
        Actor actor = JwtActorMapper.toActor(jwt);
        AssessmentResult result = useCase.assess(new AssessTransactionCommand(req.tenantId(), req.eventId(),
                req.orderId(), req.buyerId(), req.paymentReference(), req.proofImageHash(), req.amount(),
                req.expectedAmount()), actor);

        Object body = actor.is(Role.STUDENT)
                ? BuyerRiskCheckResponse.from(result.check())
                : RiskCheckResponse.from(result.check());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create("/api/fraud/checks/" + result.check().id()))
                .body(body);
    }

    @GetMapping("/{id}")
    public RiskCheckResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        return RiskCheckResponse.from(useCase.getCheck(id, JwtActorMapper.toActor(jwt)));
    }
}
