package com.yongdrel.notification.config;

import jakarta.servlet.DispatcherType;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                // Public health checks for Docker and monitoring.
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()

                // Users can only access their own notifications.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/notifications/me"
                ).authenticated()

                // Development test endpoint; authorization will be
                // further restricted by its application configuration.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/notifications/test"
                ).authenticated()

                // Deny all other endpoints unless explicitly configured.
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter()
                    )
                )
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");

            if (role == null || role.isBlank()) {
                return List.of();
            }

            return List.of(
                new SimpleGrantedAuthority("ROLE_" + role)
            );
        });

        return converter;
    }
}
