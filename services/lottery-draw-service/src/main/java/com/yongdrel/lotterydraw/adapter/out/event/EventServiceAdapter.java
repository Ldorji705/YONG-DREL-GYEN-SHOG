package com.yongdrel.lotterydraw.adapter.out.event;

import com.yongdrel.lotterydraw.application.port.EventEligibilityPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Component
public class EventServiceAdapter implements EventEligibilityPort {

    private final RestClient restClient;
    private final ZoneId eventZone;

    public EventServiceAdapter(
            RestClient.Builder builder,
            @Value("${app.services.event-url}") String eventServiceUrl,
            @Value("${app.services.event-zone:Asia/Thimphu}") String zone) {

        this.restClient = builder.baseUrl(eventServiceUrl).build();
        this.eventZone = ZoneId.of(zone);
    }

    @Override
    public void verifyEligibleForDraw(
            UUID eventId,
            String authenticatedUserId,
            String accessToken) {

        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Access token is required"
            );
        }

        JsonNode event;

        try {
            event = restClient.get()
                    .uri("/api/events/{id}", eventId)
                    .headers(headers ->
                            headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientResponseException exception) {

            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Event not found"
                );
            }

            if (exception.getStatusCode().value() == 401 ||
                    exception.getStatusCode().value() == 403) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Event Service rejected authentication"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Event Service request failed"
            );

        } catch (org.springframework.web.client.RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Event Service is unavailable",
                    exception
            );
        }

        if (event == null ||
                event.path("status").isMissingNode() ||
                event.path("createdBy").isMissingNode() ||
                event.path("endDate").isMissingNode()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Event Service returned incomplete event data"
            );
        }

        String owner = event.path("createdBy").asText();

        if (!authenticatedUserId.equals(owner)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the event owner can execute its draw"
            );
        }

        if (!"COMPLETED".equals(event.path("status").asText())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Event must be COMPLETED before drawing"
            );
        }

        LocalDateTime endDate;

        try {
            endDate = LocalDateTime.parse(
                    event.path("endDate").asText()
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Event Service returned an invalid end date",
                    exception
            );
        }

        if (endDate.isAfter(LocalDateTime.now(eventZone))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Event has not ended yet"
            );
        }
    }
}
