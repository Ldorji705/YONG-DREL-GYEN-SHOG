package com.yongdrel.payment_service.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OrganizerRolePolicy {

    private final Set<String> organizerRoles;

    public OrganizerRolePolicy(
            @Value("${app.organizer.roles:}") String configuredRoles
    ) {
        organizerRoles = Arrays.stream(configuredRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isOrganizer(String role) {
        return role != null && organizerRoles.contains(role);
    }
}
