package com.yongdrel.reportingservice.domain.service;

import com.yongdrel.reportingservice.domain.exception.StaleUpdateException;
import com.yongdrel.reportingservice.domain.model.Projection;
import com.yongdrel.reportingservice.domain.model.UpsertOutcome;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Last-writer-wins by the source's update time, with protection against out-of-order and conflicting copies:
 * newer = UPDATED, identical repeat = UNCHANGED, older or same-time-but-different = 409,
 * change of an immutable reference (tenant, event, buyer...) = 409.
 */
public final class UpsertPolicy {

    private UpsertPolicy() {
    }

    @SafeVarargs
    public static <P extends Projection> UpsertOutcome decide(P existing, P incoming, String kind,
                                                               Function<P, Object>... immutableFields) {
        if (existing == null) {
            return UpsertOutcome.CREATED;
        }
        for (Function<P, Object> field : List.of(immutableFields)) {
            if (!Objects.equals(field.apply(existing), field.apply(incoming))) {
                throw new StaleUpdateException(kind + " " + incoming.id()
                        + " cannot change its tenant/event/owner references");
            }
        }
        int cmp = incoming.sourceUpdatedAt().compareTo(existing.sourceUpdatedAt());
        if (cmp > 0) {
            return UpsertOutcome.UPDATED;
        }
        if (cmp == 0 && existing.equals(incoming)) {
            return UpsertOutcome.UNCHANGED;
        }
        throw new StaleUpdateException(kind + " " + incoming.id() + " update is "
                + (cmp < 0 ? "older than" : "in conflict with") + " the stored version ("
                + existing.sourceUpdatedAt() + ")");
    }
}
