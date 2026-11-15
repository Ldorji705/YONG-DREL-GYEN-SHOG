package com.yongdrel.fraudriskservice.adapter.out.persistence;

import com.yongdrel.fraudriskservice.domain.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "risk_flags")
public class RiskFlagEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "check_id", nullable = false) private Long checkId;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "buyer_id", nullable = false) private Long buyerId;
    @Column(nullable = false) private int score;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", nullable = false, length = 10) private RiskLevel riskLevel;
    @Column(nullable = false, length = 500) private String reasons;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FlagStatus status;
    @Column(name = "reviewer_id") private Long reviewerId;
    @Column(name = "review_note", length = 1000) private String reviewNote;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Version @Column(nullable = false) private Long version;

    protected RiskFlagEntity() {
    }

    static RiskFlagEntity from(RiskFlag value) {
        RiskFlagEntity entity = new RiskFlagEntity();
        entity.id = value.getId();
        entity.checkId = value.getCheckId();
        entity.tenantId = value.getTenantId();
        entity.eventId = value.getEventId();
        entity.orderId = value.getOrderId();
        entity.buyerId = value.getBuyerId();
        entity.score = value.getScore();
        entity.riskLevel = value.getRiskLevel();
        entity.reasons = value.getReasons().stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
        entity.status = value.getStatus();
        entity.reviewerId = value.getReviewerId();
        entity.reviewNote = value.getReviewNote();
        entity.createdAt = value.getCreatedAt();
        entity.reviewedAt = value.getReviewedAt();
        entity.version = value.getVersion();
        return entity;
    }

    RiskFlag toDomain() {
        List<RuleType> reasonTypes = reasons == null || reasons.isBlank() ? List.of()
                : Arrays.stream(reasons.split(",")).map(RuleType::valueOf).toList();
        return new RiskFlag(id, checkId, tenantId, eventId, orderId, buyerId, score, riskLevel, reasonTypes,
                status, reviewerId, reviewNote, createdAt, reviewedAt, version);
    }
}
