
package com.yongdrel.authservice.user;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
class AuthUserRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private AuthUserRepository repository;

    @Test
    void shouldSaveAndFindUserByEmail() {

        AuthUser user = new AuthUser();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole("USER");

        repository.saveAndFlush(user);

        var result = repository.findByEmail(
                "test@example.com"
        );

        assertTrue(result.isPresent());

        assertEquals(
                "test@example.com",
                result.get().getEmail()
        );
    }

    @Test
    void shouldDetectExistingEmail() {

        AuthUser user = new AuthUser();
        user.setEmail("existing@example.com");
        user.setPasswordHash("hashed-password");
        user.setRole("USER");

        repository.saveAndFlush(user);

        assertTrue(
                repository.existsByEmail(
                        "existing@example.com"
                )
        );
    }
}
