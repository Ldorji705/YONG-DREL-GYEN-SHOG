package com.yongdrel.winner.adapter.out.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class ReferenceValidationClient {

    private final RestClient eventRestClient;
    private final RestClient prizeRestClient;

    public ReferenceValidationClient(
            @Qualifier("eventRestClient") RestClient eventRestClient,
            @Qualifier("prizeRestClient") RestClient prizeRestClient) {

        this.eventRestClient = eventRestClient;
        this.prizeRestClient = prizeRestClient;
    }

    public void validateEvent(UUID eventId, String accessToken) {
        validateReference(
                eventRestClient,
                "/api/events/" + eventId,
                accessToken,
                "Event"
        );
    }

    public void validatePrize(UUID prizeId, String accessToken) {
        validateReference(
                prizeRestClient,
                "/api/prizes/" + prizeId,
                accessToken,
                "Prize"
        );
    }

    private void validateReference(
            RestClient client,
            String path,
            String accessToken,
            String referenceName) {

        try {
            client.get()
                    .uri(path)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + accessToken
                    )
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException exception) {

            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        referenceName + " reference does not exist"
                );
            }

            if (exception.getStatusCode().value() == 401
                    || exception.getStatusCode().value() == 403) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        referenceName + " service rejected the reference check"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    referenceName + " service is unavailable"
            );

        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    referenceName + " service could not be reached"
            );
        }
    }
}