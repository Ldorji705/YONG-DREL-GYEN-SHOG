package com.yongdrel.wallet_service.adapter.out.ledger;

import com.yongdrel.wallet_service.application.exception.DependencyUnavailableException;
import com.yongdrel.wallet_service.application.port.LedgerPort;
import com.yongdrel.wallet_service.domain.model.WalletOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Posts each wallet operation to the Ledger's internal endpoint.
 * referenceId = "wallet-op-{id}", so a re-send never creates a second ledger entry.
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
    public void recordOperation(WalletOperation op, String ownerId, String currency) {
        LedgerEntryRequest body = new LedgerEntryRequest(
                ownerId,
                "wallet-op-" + op.getId(),
                "WALLET",
                op.getType().name(), // CREDIT or DEBIT
                op.getAmount(),
                currency,
                op.getDescription(),
                op.getCreatedAt()
        );
        try {
            restClient.post()
                    .uri("/api/ledger/entries")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new DependencyUnavailableException("Ledger unavailable: " + e.getMessage());
        }
    }
}
