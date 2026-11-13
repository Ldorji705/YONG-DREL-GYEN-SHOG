package com.yongdrel.authservice.auth;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/admin/organizers")
@ConditionalOnProperty(
        name = "app.organizer.provisioning.enabled",
        havingValue = "true"
)
public class OrganizerProvisioningController {

    private final AuthService authService;
    private final byte[] expectedKey;

    public OrganizerProvisioningController(
            AuthService authService,
            @Value("${app.organizer.provisioning.key}") String provisioningKey
    ) {
        if (provisioningKey == null || provisioningKey.isBlank()) {
            throw new IllegalStateException(
                    "Organizer provisioning key must be configured"
            );
        }

        this.authService = authService;
        this.expectedKey = provisioningKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void provisionOrganizer(
            @RequestHeader(
                    value = "X-Admin-Provisioning-Key",
                    required = false
            ) String providedKey,
            @Valid @RequestBody OrganizerProvisioningRequest request
    ) {
        byte[] actualKey = providedKey == null
                ? new byte[0]
                : providedKey.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedKey, actualKey)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid provisioning credentials"
            );
        }

        authService.provisionOrganizer(request.email());
    }
}
