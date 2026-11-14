package com.yongdrel.claim.adapter.out.client;

import com.yongdrel.claim.domain.port.ClaimReferenceValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class ClaimReferenceValidationAdapter
        implements ClaimReferenceValidator {

    private final RestClient winnerRestClient;
    private final RestClient prizeRestClient;

    public ClaimReferenceValidationAdapter(
            @Qualifier("winnerRestClient") RestClient winnerRestClient,
            @Qualifier("prizeRestClient") RestClient prizeRestClient
    ) {
        this.winnerRestClient = winnerRestClient;
        this.prizeRestClient = prizeRestClient;
    }

    @Override
    public void validate(
            UUID winnerId,
            UUID prizeId,
            String userId,
            String accessToken
    ) {
        WinnerReferenceResponse winner = validateWinner(
                winnerId,
                accessToken
        );

        if (!userId.equals(winner.userId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Winner does not belong to the authenticated user"
            );
        }

        if (!prizeId.equals(winner.prizeId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Prize ID does not match the winner record"
            );
        }

        if (winner.verifiedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Winner has not been verified"
            );
        }

        validatePrizeExists(prizeId, accessToken);
    }

    private WinnerReferenceResponse validateWinner(
            UUID winnerId,
            String accessToken
    ) {
        try {
            WinnerReferenceResponse response = winnerRestClient.get()
                    .uri("/api/winners/{id}", winnerId)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + accessToken
                    )
                    .retrieve()
                    .body(WinnerReferenceResponse.class);

            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Winner service returned an empty response"
                );
            }

            return response;

        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Winner reference does not exist"
                );
            }

            if (exception.getStatusCode().value() == 403) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not allowed to claim this winner"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Winner service could not validate the reference"
            );
        }
    }

    private void validatePrizeExists(
            UUID prizeId,
            String accessToken
    ) {
        try {
            prizeRestClient.get()
                    .uri("/api/prizes/{id}", prizeId)
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
                        "Prize reference does not exist"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Prize service could not validate the reference"
            );
        }
    }
}