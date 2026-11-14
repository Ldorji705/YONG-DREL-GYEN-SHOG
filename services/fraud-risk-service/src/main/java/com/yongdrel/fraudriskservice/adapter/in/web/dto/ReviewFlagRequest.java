package com.yongdrel.fraudriskservice.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewFlagRequest(
        @NotNull FlagStatus status,
        @Size(max = 1000) String note
) {

    @JsonIgnore
    @AssertTrue(message = "status must be UNDER_REVIEW, CONFIRMED_FRAUD or DISMISSED")
    public boolean isReviewStatus() {
        return status != FlagStatus.OPEN;
    }

    /** Closing a flag must say why, for the audit trail. */
    @JsonIgnore
    @AssertTrue(message = "note is required when confirming or dismissing a flag")
    public boolean isNotePresentWhenClosing() {
        return status == null || !status.isTerminal() || (note != null && note.trim().length() >= 3);
    }
}
