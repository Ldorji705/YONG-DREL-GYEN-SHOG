package com.yongdrel.refund_service.adapter.out.ledger;

import com.yongdrel.refund_service.application.exception.DependencyUnavailableException;
import com.yongdrel.refund_service.application.port.LedgerPort;
import com.yongdrel.refund_service.domain.model.Refund;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Posts the refund to the Transaction Ledger's internal endpoint.
 * referenceId = "refund-{id}", so retries never create a second ledger entry.
 */
@Component
public class LedgerServiceAdapter implements LedgerPort {

    record LedgerEntryRequest(String accountId, String referenceId, String sourceType,
                              String direction, BigDecimal amount, String currency,
                              String description, Instant occurredAt) {
    }

    private final RestClient restClient;
    private final String internalApiKey;

    public LedgerServiceAdapter(
            @Value("${app.ledger.base-url}") String baseUrl,
            @Value("${app.ledger.internal-api-key:}") String internalApiKey
    ) {
        this.restClient = RestClient.create(baseUrl);
        this.internalApiKey = internalApiKey;
    }

    @Override
    public void recordRefund(Refund refund) {
        LedgerEntryRequest body = new LedgerEntryRequest(
                refund.getUserId(),
                "refund-" + refund.getId(),
                "REFUND",
                "CREDIT",
                refund.getAmount(),
                refund.getCurrency(),
                "Refund for payment " + refund.getPaymentId(),
                refund.getCreatedAt()
        );
        try {
            restClient.post()
                    .uri("/api/ledger/entries")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity(); // 201 created or 200 already recorded; anything else throws
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("Ledger could not record refund: " + e.getMessage());
        }
    }
}
