package com.yongdrel.auditlogservice.adapter.in.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yongdrel.auditlogservice.domain.exception.InvalidAuditEventException;

import java.util.Map;

/** Canonical JSON (sorted keys) for the free-form details object, so the integrity hash is stable. */
public final class DetailsJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private DetailsJson() {
    }

    public static String write(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new InvalidAuditEventException("details is not serializable");
        }
    }

    public static Map<String, Object> read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            return Map.of("_unreadable", json);
        }
    }
}
