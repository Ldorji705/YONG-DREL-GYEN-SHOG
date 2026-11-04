package com.yongdrel.prize.adapter.in.web;

import com.yongdrel.prize.adapter.in.web.dto.PrizeRequest;
import com.yongdrel.prize.adapter.out.persistence.PrizeEntity;
import com.yongdrel.prize.application.service.PrizeApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prizes")
public class PrizeController {

    private final PrizeApplicationService service;

    public PrizeController(PrizeApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrizeEntity create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody PrizeRequest request) {
        return service.create(request, jwt.getSubject());
    }

    @GetMapping
    public List<PrizeEntity> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PrizeEntity get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public PrizeEntity update(
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody PrizeRequest request) {
        return service.update(id, request, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable UUID id,
        @AuthenticationPrincipal Jwt jwt) {
        service.delete(id, jwt.getSubject());
    }
}
