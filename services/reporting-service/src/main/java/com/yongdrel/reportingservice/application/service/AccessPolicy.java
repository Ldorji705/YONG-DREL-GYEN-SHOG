package com.yongdrel.reportingservice.application.service;

import com.yongdrel.reportingservice.application.exception.ForbiddenOperationException;
import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.Role;

/**
 * Reports: ORGANIZER only for its own tenant, ADMIN for any tenant (or platform-wide).
 * Students only see their own purchases. SERVICE accounts only feed data in.
 */
final class AccessPolicy {

    private AccessPolicy() {
    }

    /** Returns the tenant the report must be restricted to (null = platform-wide, admins only). */
    static Long reportTenant(Caller caller, Long requestedTenant) {
        return switch (caller.role()) {
            case ADMIN -> requestedTenant;
            case ORGANIZER -> {
                Long own = requireTenantClaim(caller);
                if (requestedTenant != null && !requestedTenant.equals(own)) {
                    throw new ForbiddenOperationException("Tenant " + requestedTenant + " does not belong to the caller");
                }
                yield own;
            }
            case STUDENT -> throw new ForbiddenOperationException("Students can only view their own purchases");
            case SERVICE -> throw new ForbiddenOperationException("Service accounts cannot read reports");
        };
    }

    static boolean canSeeTenant(Caller caller, Long tenantId) {
        return caller.is(Role.ADMIN) || (caller.is(Role.ORGANIZER) && tenantId.equals(caller.tenantId()));
    }

    static void requireIngestor(Caller caller) {
        if (caller != null && !caller.is(Role.SERVICE) && !caller.is(Role.ADMIN)) {
            throw new ForbiddenOperationException("Only services and admins can feed reporting data");
        }
    }

    static void requireStudent(Caller caller) {
        if (!caller.is(Role.STUDENT)) {
            throw new ForbiddenOperationException("Only students have personal purchase reports");
        }
    }

    static Long requireOrganizerTenant(Caller caller) {
        if (!caller.is(Role.ORGANIZER)) {
            throw new ForbiddenOperationException("Only organizers can manage saved reports");
        }
        return requireTenantClaim(caller);
    }

    private static Long requireTenantClaim(Caller caller) {
        if (caller.tenantId() == null) {
            throw new ForbiddenOperationException("Organizer token has no tenantId claim");
        }
        return caller.tenantId();
    }
}
