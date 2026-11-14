package com.yongdrel.reportingservice.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Uniform error body for every 4xx/5xx response of this service. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
