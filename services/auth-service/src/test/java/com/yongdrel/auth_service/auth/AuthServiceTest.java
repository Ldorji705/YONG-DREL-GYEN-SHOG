
package com.yongdrel.authservice.auth;

import com.yongdrel.authservice.user.AuthUser;
import com.yongdrel.authservice.user.AuthUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
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
        testUser.setId(1L);
        testUser.setEmail("sonam@example.com");
        testUser.setPasswordHash("hashed-password");
        testUser.setRole("USER");
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
                && user.getRole().equals("USER")
        ));

        verify(passwordEncoder)
                .encode("TestPassword123!");
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

