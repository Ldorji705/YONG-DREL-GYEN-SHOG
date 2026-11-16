package com.yongdrel.wallet_service.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * - GET /api/wallets/me and POST /api/wallets/debit: any valid user JWT; always the caller's own wallet.
 * - POST /api/wallets/credit: internal services (X-Internal-Api-Key) or ADMIN only,
 *   otherwise users could give themselves money.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.wallet.internal-api-key:}") String internalApiKey
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalApiKeyFilter(internalApiKey), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/wallets/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/wallets/debit").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/wallets/credit")
                        .hasAnyRole(InternalApiKeyFilter.ROLE, "ADMIN")

                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .build();
    }

    /** Maps the JWT "role" claim (USER / ADMIN / ...) to ROLE_x, like the other services. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
