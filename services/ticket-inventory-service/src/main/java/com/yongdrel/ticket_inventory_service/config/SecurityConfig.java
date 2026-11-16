package com.yongdrel.ticket_inventory_service.config;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration public class SecurityConfig {
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
  return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
    .requestMatchers("/actuator/health","/actuator/info").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/ticket-inventory/**").authenticated()
    .requestMatchers(HttpMethod.POST,"/api/ticket-inventory/**").authenticated().requestMatchers("/internal/ticket-inventory/**").permitAll().anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(j->{})).build();
 }
}
