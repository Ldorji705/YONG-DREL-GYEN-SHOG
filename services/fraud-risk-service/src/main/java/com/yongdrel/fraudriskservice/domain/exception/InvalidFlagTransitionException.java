package com.yongdrel.fraudriskservice.domain.exception;

import com.yongdrel.fraudriskservice.domain.model.FlagStatus;

/** A review action that is not allowed from the flag's current status (mapped to 409). */
public class InvalidFlagTransitionException extends RuntimeException {
    public InvalidFlagTransitionException(FlagStatus from, FlagStatus to) {
        super("Flag cannot move from " + from + " to " + to);
    }
}
