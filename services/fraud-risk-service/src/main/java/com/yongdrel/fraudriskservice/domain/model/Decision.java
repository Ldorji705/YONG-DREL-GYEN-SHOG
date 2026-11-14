package com.yongdrel.fraudriskservice.domain.model;

/** Outcome of a risk check. REVIEW_REQUIRED means a flag was raised for manual review. */
public enum Decision {
    CLEAR, REVIEW_REQUIRED
}
