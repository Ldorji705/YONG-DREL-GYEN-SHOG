package com.yongdrel.auditlogservice.domain.model;

/** Kind of business entity an audit event is about. targetId is that entity's id in its owning service. */
public enum TargetType {
    USER, TENANT, EVENT, TICKET, PRIZE, ORDER, PAYMENT, PAYMENT_PROOF, DRAW, WINNER,
    NOTIFICATION, RISK_CHECK, RISK_FLAG, RISK_RULE, REPORT, SYSTEM
}
