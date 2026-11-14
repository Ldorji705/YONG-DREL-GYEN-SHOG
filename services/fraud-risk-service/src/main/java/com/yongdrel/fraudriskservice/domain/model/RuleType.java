package com.yongdrel.fraudriskservice.domain.model;

/**
 * The configurable checks the Fraud &amp; Risk Service can apply to a payment submission.
 * Count-based rules trigger when the observed count is &gt;= threshold;
 * AMOUNT_MISMATCH triggers when the difference (in percent) is &gt; threshold.
 */
public enum RuleType {

    DUPLICATE_PAYMENT_REFERENCE(true, "Same bank/UPI reference number already submitted for another order"),
    DUPLICATE_PROOF_IMAGE(true, "Same payment screenshot (SHA-256) already submitted for another order"),
    BUYER_VELOCITY(true, "Buyer submitted too many payment proofs within the time window"),
    AMOUNT_MISMATCH(false, "Declared amount differs from the expected ticket price by more than threshold percent"),
    PRIOR_CONFIRMED_FRAUD(true, "Buyer already has flags confirmed as fraud by an organizer");

    private final boolean countBased;
    private final String description;

    RuleType(boolean countBased, String description) {
        this.countBased = countBased;
        this.description = description;
    }

    public boolean isCountBased() {
        return countBased;
    }

    public String getDescription() {
        return description;
    }
}
