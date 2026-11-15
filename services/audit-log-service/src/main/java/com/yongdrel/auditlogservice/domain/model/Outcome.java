package com.yongdrel.auditlogservice.domain.model;

/** DENIED = blocked by an authorization or business rule; FAILURE = attempted but failed. */
public enum Outcome {
    SUCCESS, FAILURE, DENIED
}
