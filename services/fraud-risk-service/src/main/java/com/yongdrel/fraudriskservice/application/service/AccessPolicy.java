package com.yongdrel.fraudriskservice.application.service;

import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.Role;

import java.util.Objects;

/**
 * Role / tenant / ownership rules of the Fraud &amp; Risk Service.
 * ADMIN and SERVICE act platform-wide; ORGANIZER is confined to its own tenant;
 * STUDENT may only submit checks for their own purchases.
 */
final class AccessPolicy {

    private AccessPolicy() {
    }

    static void requireCanAssess(Actor actor, Long tenantId, Long buyerId) {
        switch (actor.role()) {
            case ADMIN, SERVICE -> { }
            case ORGANIZER -> requireOwnTenant(actor, tenantId);
            case STUDENT -> {
                if (!Objects.equals(actor.userId(), buyerId)) {
                    throw new ForbiddenOperationException("Students can only submit checks for their own orders");
                }
            }
        }
    }

    /** Whether the actor may read checks/flags of the tenant. Students never read risk data. */
    static boolean canViewTenant(Actor actor, Long tenantId) {
        return actor.hasPlatformWideAccess()
                || (actor.is(Role.ORGANIZER) && actor.belongsToTenant(tenantId));
    }

    static void requireRiskReader(Actor actor) {
        if (actor.is(Role.STUDENT)) {
            throw new ForbiddenOperationException("Students cannot view risk data");
        }
    }

    static void requireReviewer(Actor actor) {
        if (!actor.is(Role.ADMIN) && !actor.is(Role.ORGANIZER)) {
            throw new ForbiddenOperationException("Only organizers and admins can review flags");
        }
    }

    /** Admins manage everything; organizers only rules of their own tenant (never platform defaults). */
    static void requireRuleManager(Actor actor, Long tenantId) {
        if (actor.is(Role.ADMIN)) {
            return;
        }
        if (!actor.is(Role.ORGANIZER)) {
            throw new ForbiddenOperationException("Only organizers and admins can manage rules");
        }
        if (tenantId == null) {
            throw new ForbiddenOperationException("Only admins can change platform default rules");
        }
        requireOwnTenant(actor, tenantId);
    }

    /** Organizers may read platform defaults and their own tenant's rules. */
    static void requireRuleReader(Actor actor, Long tenantId) {
        if (actor.is(Role.ADMIN)) {
            return;
        }
        if (!actor.is(Role.ORGANIZER)) {
            throw new ForbiddenOperationException("Only organizers and admins can view rules");
        }
        if (tenantId != null) {
            requireOwnTenant(actor, tenantId);
        }
    }

    static void requireOwnTenant(Actor actor, Long tenantId) {
        if (actor.tenantId() == null) {
            throw new ForbiddenOperationException("Organizer token has no tenantId claim");
        }
        if (!actor.belongsToTenant(tenantId)) {
            throw new ForbiddenOperationException("Tenant " + tenantId + " does not belong to the caller");
        }
    }
}
