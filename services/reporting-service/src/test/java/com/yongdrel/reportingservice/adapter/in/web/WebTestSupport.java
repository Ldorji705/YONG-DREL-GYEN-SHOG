package com.yongdrel.reportingservice.adapter.in.web;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Builds JWTs shaped like the Auth Service's tokens (sub, role, tenantId). */
final class WebTestSupport {

    private WebTestSupport() {
    }

    static RequestPostProcessor organizer() {
        return token("3", "ORGANIZER", 10L);
    }

    static RequestPostProcessor otherOrganizer() {
        return token("4", "ORGANIZER", 20L);
    }

    static RequestPostProcessor admin() {
        return token("1", "ADMIN", null);
    }

    static RequestPostProcessor service() {
        return token("2", "SERVICE", null);
    }

    /** The Auth Service currently issues role=USER for students. */
    static RequestPostProcessor student() {
        return token("100", "USER", null);
    }

    static RequestPostProcessor token(String subject, String role, Long tenantId) {
        String authority = "ROLE_" + ("USER".equals(role) ? "STUDENT" : role);
        SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt = jwt()
                .jwt(builder -> {
                    builder.subject(subject).claim("role", role);
                    if (tenantId != null) {
                        builder.claim("tenantId", tenantId);
                    }
                })
                .authorities(new SimpleGrantedAuthority(authority));
        return jwt;
    }
}
