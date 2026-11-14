package com.yongdrel.auditlogservice;

import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;
import com.yongdrel.auditlogservice.domain.model.Caller;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.Role;
import com.yongdrel.auditlogservice.domain.model.TargetType;

import java.time.Instant;
import java.util.UUID;

/** Tenant 10 = our organizer; student 100 is the actor of the sample event. */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    public static final UUID EVENT_ID = UUID.fromString("3f2b8c1e-7a4d-4e1b-9c55-0a1b2c3d4e5f");
    public static final String CORRELATION = "order-5001-checkout";

    public static final Caller ADMIN = new Caller(1L, Role.ADMIN, null);
    public static final Caller SERVICE = new Caller(2L, Role.SERVICE, null);
    public static final Caller ORGANIZER = new Caller(3L, Role.ORGANIZER, 10L);
    public static final Caller OTHER_ORGANIZER = new Caller(4L, Role.ORGANIZER, 20L);
    public static final Caller ORGANIZER_WITHOUT_TENANT = new Caller(5L, Role.ORGANIZER, null);
    public static final Caller STUDENT = new Caller(100L, Role.STUDENT, null);
    public static final Caller OTHER_STUDENT = new Caller(101L, Role.STUDENT, null);

    private TestData() {
    }

    public static AuditEventDraft draft() {
        return new AuditEventDraft(EVENT_ID, 10L, ActorType.USER, 100L, Role.STUDENT, "PAYMENT_PROOF_UPLOADED",
                Outcome.SUCCESS, TargetType.ORDER, "5001", "payment-proof-service", CORRELATION,
                NOW.minusSeconds(5), "{\"amount\":100}");
    }

    public static AuditEvent event(Long id) {
        return AuditEvent.create(draft(), NOW).withId(id);
    }
}
