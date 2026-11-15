package com.yongdrel.fraudriskservice.adapter.out.persistence;

import com.yongdrel.fraudriskservice.domain.model.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "risk_checks")
public class RiskCheckEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64) private String idempotencyKey;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "buyer_id", nullable = false) private Long buyerId;
    @Column(name = "payment_reference", length = 64) private String paymentReference;
    @Column(name = "proof_image_hash", length = 64) private String proofImageHash;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "expected_amount", precision = 12, scale = 2) private BigDecimal expectedAmount;
    @Column(nullable = false) private int score;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", nullable = false, length = 10) private RiskLevel riskLevel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Decision decision;
    @Column(name = "flag_id") private Long flagId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @ElementCollection
    @CollectionTable(name = "risk_check_findings", joinColumns = @JoinColumn(name = "check_id"))
    private List<RiskFindingEmbeddable> findings = new ArrayList<>();

    protected RiskCheckEntity() {
    }

    static RiskCheckEntity from(RiskCheck value) {
        RiskCheckEntity entity = new RiskCheckEntity();
        entity.id = value.id();
        entity.idempotencyKey = value.idempotencyKey();
        TransactionDetails tx = value.transaction();
        entity.tenantId = tx.tenantId();
        entity.eventId = tx.eventId();
        entity.orderId = tx.orderId();
        entity.buyerId = tx.buyerId();
        entity.paymentReference = tx.paymentReference();
        entity.proofImageHash = tx.proofImageHash();
        entity.amount = tx.amount();
        entity.expectedAmount = tx.expectedAmount();
        entity.score = value.assessment().score();
        entity.riskLevel = value.assessment().level();
        entity.decision = value.assessment().decision();
        entity.flagId = value.flagId();
        entity.createdAt = value.createdAt();
        entity.findings = value.assessment().findings().stream().map(RiskFindingEmbeddable::from).toList();
        return entity;
    }

    RiskCheck toDomain() {
        TransactionDetails tx = new TransactionDetails(tenantId, eventId, orderId, buyerId, paymentReference,
                proofImageHash, amount, expectedAmount);
        RiskAssessment assessment = new RiskAssessment(score, riskLevel, decision,
                findings.stream().map(RiskFindingEmbeddable::toDomain).toList());
        return new RiskCheck(id, idempotencyKey, tx, assessment, flagId, createdAt);
    }
}
