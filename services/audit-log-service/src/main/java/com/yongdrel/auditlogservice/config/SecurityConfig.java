package com.yongdrel.auditlogservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongdrel.auditlogservice.adapter.in.web.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Coarse role checks per endpoint. Fine-grained tenant/ownership checks live in the application layer
 * (AccessPolicy) so they are unit-tested independently of HTTP.
 */
@Configuration
public class SecurityConfig {

    /** Services write the audit trail; users never write their own. */
    private static final String[] WRITERS = {"ADMIN", "SERVICE"};
    /** Reads are further scoped per role in AccessPolicy (tenant / own activity). SERVICE cannot read. */
    private static final String[] READERS = {"ADMIN", "ORGANIZER", "STUDENT"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, ex) ->
                write(objectMapper, req, res, HttpStatus.UNAUTHORIZED, "Missing, expired or invalid bearer token");
        AccessDeniedHandler denied = (req, res, ex) ->
                write(objectMapper, req, res, HttpStatus.FORBIDDEN, "Your role is not allowed to call this endpoint");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new RoleClaimConverter());

        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/audit/events").hasAnyRole(WRITERS)
                        .requestMatchers(HttpMethod.GET, "/api/audit/**").hasAnyRole(READERS)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(denied))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorized).accessDeniedHandler(denied))
                .build();
    }

    private static void write(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res,
                              HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            res.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(res.getOutputStream(), new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), message, req.getRequestURI(), List.of()));
    }
}
