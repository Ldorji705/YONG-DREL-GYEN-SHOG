package com.yongdrel.reportingservice.config;

import com.yongdrel.reportingservice.adapter.in.web.JwtCallerMapper;
import com.yongdrel.reportingservice.domain.model.Role;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/** Maps the JWT "role" claim (e.g. ORGANIZER, USER) to a Spring authority (ROLE_ORGANIZER, ROLE_STUDENT). */
public class RoleClaimConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return Role.fromClaim(jwt.getClaimAsString(JwtCallerMapper.ROLE_CLAIM))
                .<Collection<GrantedAuthority>>map(role -> List.of(new SimpleGrantedAuthority("ROLE_" + role.name())))
                .orElse(List.of());
    }
}
