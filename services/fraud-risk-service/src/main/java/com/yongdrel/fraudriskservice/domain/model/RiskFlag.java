package com.yongdrel.fraudriskservice.domain.model;

import com.yongdrel.fraudriskservice.domain.exception.InvalidFlagTransitionException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A suspicious submission waiting for (or having had) manual review by the organizer. */
public class RiskFlag {

    private final Long id;
    private final Long checkId;
    private final Long tenantId;
    private final Long eventId;
    private final Long orderId;
    private final Long buyerId;
    private final int score;
    private final RiskLevel riskLevel;
    private final List<RuleType> reasons;
    private final Instant createdAt;
    private final Long version;
    private FlagStatus status;
    private Long reviewerId;
    private String reviewNote;
    private Instant reviewedAt;

    public RiskFlag(Long id, Long checkId, Long tenantId, Long eventId, Long orderId, Long buyerId,
                    int score, RiskLevel riskLevel, List<RuleType> reasons, FlagStatus status,
                    Long reviewerId, String reviewNote, Instant createdAt, Instant reviewedAt, Long version) {
        this.id = id;
        this.checkId = checkId;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.eventId = eventId;
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.score = score;
        this.riskLevel = riskLevel;
        this.reasons = List.copyOf(reasons);
        this.status = Objects.requireNonNull(status, "status");
        this.reviewerId = reviewerId;
        this.reviewNote = reviewNote;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
        this.version = version;
    }

    public static RiskFlag raise(RiskCheck check, Instant now) {
        TransactionDetails tx = check.transaction();
        List<RuleType> reasons = check.assessment().findings().stream()
                .map(RuleFinding::ruleType)
                .toList();
        return new RiskFlag(null, check.id(), tx.tenantId(), tx.eventId(), tx.orderId(), tx.buyerId(),
                check.assessment().score(), check.assessment().level(), reasons, FlagStatus.OPEN,
                null, null, now, null, null);
    }

    public void review(FlagStatus target, Long reviewer, String note, Instant now) {
        Objects.requireNonNull(target, "target");
        if (!status.canMoveTo(target)) {
            throw new InvalidFlagTransitionException(status, target);
        }
        this.status = target;
        this.reviewerId = reviewer;
        this.reviewNote = note == null || note.isBlank() ? null : note.trim();
        this.reviewedAt = now;
    }

    public Long getId() { return id; }
    public Long getCheckId() { return checkId; }
    public Long getTenantId() { return tenantId; }
    public Long getEventId() { return eventId; }
    public Long getOrderId() { return orderId; }
    public Long getBuyerId() { return buyerId; }
    public int getScore() { return score; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public List<RuleType> getReasons() { return reasons; }
    public FlagStatus getStatus() { return status; }
    public Long getReviewerId() { return reviewerId; }
    public String getReviewNote() { return reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Long getVersion() { return version; }
}
