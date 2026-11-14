package com.yongdrel.reportingservice.adapter.in.web;

import com.yongdrel.reportingservice.adapter.in.web.dto.SavedReportDtos.SavedReportRequest;
import com.yongdrel.reportingservice.adapter.in.web.dto.SavedReportDtos.SavedReportResponse;
import com.yongdrel.reportingservice.application.port.in.SavedReportUseCase;
import com.yongdrel.reportingservice.domain.model.SavedReport;

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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** CRUD for an organizer's saved report definitions, plus running them. */
@RestController
@RequestMapping("/api/reports/saved")
public class SavedReportController {

    private final SavedReportUseCase saved;

    public SavedReportController(SavedReportUseCase saved) {
        this.saved = saved;
    }

    @PostMapping
    public ResponseEntity<SavedReportResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody SavedReportRequest request) {
        SavedReport r = saved.create(request.toCommand(), JwtCallerMapper.toCaller(jwt));
        return ResponseEntity.created(URI.create("/api/reports/saved/" + r.getId())).body(SavedReportResponse.from(r));
    }

    @GetMapping
    public List<SavedReportResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return saved.list(JwtCallerMapper.toCaller(jwt)).stream().map(SavedReportResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SavedReportResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        return SavedReportResponse.from(saved.get(id, JwtCallerMapper.toCaller(jwt)));
    }

    @PutMapping("/{id}")
    public SavedReportResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id,
                                      @Valid @RequestBody SavedReportRequest request) {
        return SavedReportResponse.from(saved.update(id, request.toCommand(), JwtCallerMapper.toCaller(jwt)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        saved.delete(id, JwtCallerMapper.toCaller(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/run")
    public Object run(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long id) {
        return saved.run(id, JwtCallerMapper.toCaller(jwt));
    }
}
