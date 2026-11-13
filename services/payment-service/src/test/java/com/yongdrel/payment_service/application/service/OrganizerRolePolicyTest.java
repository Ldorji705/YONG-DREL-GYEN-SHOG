package com.yongdrel.payment_service.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizerRolePolicyTest {

    @Test
    void rejectsAuthServiceUserRole() {
        OrganizerRolePolicy policy = new OrganizerRolePolicy("");

        assertFalse(policy.isOrganizer("USER"));
        assertFalse(policy.isOrganizer("ORGANIZER"));
        assertFalse(policy.isOrganizer(null));
    }

    @Test
    void acceptsOnlyExplicitlyConfiguredRole() {
        OrganizerRolePolicy policy =
                new OrganizerRolePolicy("ORGANIZER,LOTTERY_ORGANIZER");

        assertTrue(policy.isOrganizer("ORGANIZER"));
        assertTrue(policy.isOrganizer("LOTTERY_ORGANIZER"));
        assertFalse(policy.isOrganizer("ADMIN"));
        assertFalse(policy.isOrganizer("organizer"));
    }
}
