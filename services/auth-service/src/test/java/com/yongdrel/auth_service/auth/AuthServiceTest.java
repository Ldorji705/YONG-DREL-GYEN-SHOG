
package com.yongdrel.authservice.auth;

import com.yongdrel.authservice.user.AuthUser;
import com.yongdrel.authservice.user.AuthUserRepository;
import com.yongdrel.authservice.user.UserRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.UUID;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthUserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private AuthService authService;

    private AuthUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new AuthUser();
        testUser.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        testUser.setEmail("sonam@example.com");
        testUser.setPasswordHash("hashed-password");
        testUser.setRole(UserRole.USER);
    }

    @Test
    void register_shouldSaveNewUser() {

        RegisterRequest request = new RegisterRequest(
                "sonam@example.com",
                "TestPassword123!"
        );

        when(repository.existsByEmail("sonam@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("TestPassword123!"))
                .thenReturn("hashed-password");

        authService.register(request);

        verify(repository).save(argThat(user ->
                user.getEmail().equals("sonam@example.com")
                && user.getPasswordHash().equals("hashed-password")
                && user.getRole() == UserRole.USER
        ));

        verify(passwordEncoder)
                .encode("TestPassword123!");
    }

    @Test
    void register_shouldAlwaysCreateUserRole() {
        RegisterRequest request = new RegisterRequest(
                "new@example.com",
                "TestPassword123!"
        );

        when(repository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("TestPassword123!"))
                .thenReturn("hashed-password");

        authService.register(request);

        verify(repository).save(argThat(user ->
                user.getRole() == UserRole.USER
        ));
    }

    @Test
    void provisionOrganizer_shouldPromoteExistingUser() {
        when(repository.findByEmail("sonam@example.com"))
                .thenReturn(Optional.of(testUser));

        authService.provisionOrganizer("SONAM@example.com");

        assertEquals(UserRole.ORGANIZER, testUser.getRole());
        verify(repository).save(testUser);
    }

    @Test
    void provisionOrganizer_shouldRejectUnknownUser() {
        when(repository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.provisionOrganizer("unknown@example.com")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void provisionOrganizer_shouldRejectInvalidRoleValuesAtTheModelBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserRole.valueOf("ADMIN")
        );
    }

    @Test
    void login_shouldIssueOrganizerJwtWithExistingClaims() {
        testUser.setRole(UserRole.ORGANIZER);
        when(repository.findByEmail("sonam@example.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(
                "TestPassword123!",
                "hashed-password"
        )).thenReturn(true);

        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("signed-token");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(jwt);
        ReflectionTestUtils.setField(
                authService, "issuer", "yongdrel-auth"
        );
        ReflectionTestUtils.setField(
                authService, "audience", "yongdrel-api"
        );
        ReflectionTestUtils.setField(
                authService, "expirationMinutes", 30L
        );

        TokenResponse response = authService.login(new LoginRequest(
                "sonam@example.com",
                "TestPassword123!"
        ));

        assertEquals("signed-token", response.accessToken());
        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        assertEquals(
                "yongdrel-auth",
                captor.getValue().getClaims().getClaimAsString("iss")
        );
        assertEquals(
                testUser.getId().toString(),
                captor.getValue().getClaims().getSubject()
        );
        assertEquals(
                "yongdrel-api",
                captor.getValue().getClaims().getAudience().get(0)
        );
        assertEquals(
                "ORGANIZER",
                captor.getValue().getClaims().getClaimAsString("role")
        );
    }

    @Test
    void authUser_shouldRejectNullRole() {
        assertThrows(
                NullPointerException.class,
                () -> testUser.setRole(null)
        );
    }

    @Test
    void register_shouldRejectDuplicateEmail() {

        RegisterRequest request = new RegisterRequest(
                "sonam@example.com",
                "TestPassword123!"
        );

        when(repository.existsByEmail("sonam@example.com"))
                .thenReturn(true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.register(request)
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(repository, never()).save(any());
    }

    @Test
    void login_shouldRejectIncorrectPassword() {

        LoginRequest request = new LoginRequest(
                "sonam@example.com",
                "WrongPassword123!"
        );

        when(repository.findByEmail("sonam@example.com"))
                .thenReturn(Optional.of(testUser));

        when(passwordEncoder.matches(
                "WrongPassword123!",
                "hashed-password"
        )).thenReturn(false);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.login(request)
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void login_shouldRejectUnknownEmail() {

        LoginRequest request = new LoginRequest(
                "unknown@example.com",
                "TestPassword123!"
        );

        when(repository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.login(request)
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(jwtEncoder);
    }
}
