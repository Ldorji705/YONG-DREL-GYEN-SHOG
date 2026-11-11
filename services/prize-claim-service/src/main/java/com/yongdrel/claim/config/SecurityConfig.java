
package com.yongdrel.claim.config;

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
                .requestMatchers("/actuator/health").permitAll()

                // Administrators can view all claims.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/claims"
                ).hasRole("ADMIN")

                // Administrators manage claim review and status changes.
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/claims/*/review",
                    "/api/claims/*/approve",
                    "/api/claims/*/reject",
                    "/api/claims/*/fulfill"
                ).hasRole("ADMIN")

                // Users can submit claims and view their own claims.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/claims"
                ).authenticated()

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/claims/me"
                ).authenticated()

                // Individual claim access requires authentication.
                // The controller must enforce ownership.
                .requestMatchers("/api/claims/**").authenticated()

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
