package com.yongdrel.auditlogservice.application.service;

import com.yongdrel.auditlogservice.application.exception.ForbiddenOperationException;
import com.yongdrel.auditlogservice.application.model.AuditSearchCriteria;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.model.Caller;
import com.yongdrel.auditlogservice.domain.model.Role;

import java.util.Objects;

/**
 * Who may write and read the audit log.
 * Write: SERVICE and ADMIN only (users never write their own audit trail).
 * Read: ADMIN everything; ORGANIZER only its tenant; STUDENT only events where they are the actor;
 * SERVICE cannot read (least privilege).
 */
final class AccessPolicy {

    private AccessPolicy() {
    }

    static void requireWriter(Caller caller) {
        if (!caller.is(Role.SERVICE) && !caller.is(Role.ADMIN)) {
            throw new ForbiddenOperationException("Only services and admins can record audit events");
        }
    }

    /** Narrows the filters to what the caller may see, or refuses if they asked for someone else's data. */
    static AuditSearchCriteria scope(AuditSearchCriteria criteria, Caller caller) {
        return switch (caller.role()) {
            case ADMIN -> criteria;
            case ORGANIZER -> {
                if (caller.tenantId() == null) {
                    throw new ForbiddenOperationException("Organizer token has no tenantId claim");
                }
                if (criteria.tenantId() != null && !criteria.tenantId().equals(caller.tenantId())) {
                    throw new ForbiddenOperationException("Tenant " + criteria.tenantId() + " does not belong to the caller");
                }
                yield criteria.withTenantId(caller.tenantId());
            }
            case STUDENT -> {
                if (criteria.actorId() != null && !criteria.actorId().equals(caller.userId())) {
                    throw new ForbiddenOperationException("Students can only view their own activity");
                }
                yield criteria.withActorId(caller.userId());
            }
            case SERVICE -> throw new ForbiddenOperationException("Service accounts cannot read the audit log");
        };
    }

    static boolean canView(AuditEvent event, Caller caller) {
        return switch (caller.role()) {
            case ADMIN -> true;
            case ORGANIZER -> caller.tenantId() != null && caller.tenantId().equals(event.tenantId());
            case STUDENT -> Objects.equals(caller.userId(), event.actorId());
            case SERVICE -> false;
        };
    }
}
