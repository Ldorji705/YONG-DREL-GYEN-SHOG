package com.yongdrel.auth_service;

import com.yongdrel.authservice.AuthServiceApplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = AuthServiceApplication.class,
    properties = {
        "spring.profiles.active=test",
        "app.jwt.private-key=file:../../infrastructure/keys/private.pem",
        "app.jwt.public-key=file:../../infrastructure/keys/public.pem"
    }
)
@Testcontainers
class AuthServiceApplicationTests {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql =
        new MySQLContainer<>("mysql:8.4");

    @Test
    void contextLoads() {
    }
}