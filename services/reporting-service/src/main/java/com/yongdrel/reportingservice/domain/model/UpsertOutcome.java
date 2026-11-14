package com.yongdrel.reportingservice.domain.model;

/** Result of applying an incoming projection to the stored copy. */
public enum UpsertOutcome {
    CREATED, UPDATED, UNCHANGED
}
