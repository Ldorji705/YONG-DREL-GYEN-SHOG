-- Platform default rules. Organizers can override any of these per tenant via POST /api/fraud/rules.
INSERT INTO risk_rules (tenant_id, rule_type, enabled, threshold, window_minutes, weight, description, updated_at)
VALUES
    (NULL, 'DUPLICATE_PAYMENT_REFERENCE', TRUE, 1, 0,   70, 'Same bank/UPI reference number already submitted for another order', UTC_TIMESTAMP(6)),
    (NULL, 'DUPLICATE_PROOF_IMAGE',       TRUE, 1, 0,   70, 'Same payment screenshot already submitted for another order',       UTC_TIMESTAMP(6)),
    (NULL, 'BUYER_VELOCITY',              TRUE, 5, 10,  25, 'Buyer submitted 5 or more payment proofs within 10 minutes',        UTC_TIMESTAMP(6)),
    (NULL, 'AMOUNT_MISMATCH',             TRUE, 0, 0,   40, 'Declared amount differs from the ticket price',                     UTC_TIMESTAMP(6)),
    (NULL, 'PRIOR_CONFIRMED_FRAUD',       TRUE, 1, 0,   50, 'Buyer already has a flag confirmed as fraud',                       UTC_TIMESTAMP(6));
