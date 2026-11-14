package com.yongdrel.auditlogservice.domain.model;

/** Who performed the audited action: a logged-in user, a service account, or the platform itself (scheduler, saga). */
public enum ActorType {
    USER, SERVICE, SYSTEM
}
