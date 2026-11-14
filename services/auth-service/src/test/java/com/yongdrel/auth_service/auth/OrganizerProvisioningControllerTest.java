package com.yongdrel.authservice.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OrganizerProvisioningControllerTest {

    @Test
    void provisioning_shouldRejectInvalidSecret() {
        AuthService authService = mock(AuthService.class);
        OrganizerProvisioningController controller =
                new OrganizerProvisioningController(authService, "managed-secret");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.provisionOrganizer(
                        "wrong-secret",
                        new OrganizerProvisioningRequest("user@example.com")
                )
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
        verifyNoInteractions(authService);
    }

    @Test
    void provisioning_shouldPromoteOnlyWithValidSecret() {
        AuthService authService = mock(AuthService.class);
        OrganizerProvisioningController controller =
                new OrganizerProvisioningController(authService, "managed-secret");

        controller.provisionOrganizer(
                "managed-secret",
                new OrganizerProvisioningRequest("User@Example.com")
        );

        verify(authService).provisionOrganizer("User@Example.com");
    }

    @Test
    void provisioning_shouldRejectBlankConfiguredSecret() {
        assertThrows(
                IllegalStateException.class,
                () -> new OrganizerProvisioningController(
                        mock(AuthService.class),
                        " "
                )
        );
    }
}
