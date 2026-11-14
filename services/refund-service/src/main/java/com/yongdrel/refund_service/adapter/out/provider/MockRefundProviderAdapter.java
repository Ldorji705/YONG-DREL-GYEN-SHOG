package com.yongdrel.refund_service.adapter.out.provider;

import com.yongdrel.refund_service.application.exception.DependencyUnavailableException;
import com.yongdrel.refund_service.application.port.RefundProviderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deterministic mock of a payment provider's refund API (handbook: "mock/sandbox provider").
 * mode=SUCCESS always succeeds; mode=FAIL always fails, to exercise the FAILED + retry path.
 * The reference is derived from the refund ID, so repeating a call gives the same reference.
 */
@Component
public class MockRefundProviderAdapter implements RefundProviderPort {

    private final String mode;

    public MockRefundProviderAdapter(@Value("${app.refund.provider.mode:SUCCESS}") String mode) {
        this.mode = mode;
    }

    @Override
    public String refund(UUID refundId, BigDecimal amount, String currency) {
        if ("FAIL".equalsIgnoreCase(mode)) {
            throw new DependencyUnavailableException("Mock provider declined the refund");
        }
        return "MOCK-RF-" + refundId;
    }
}
