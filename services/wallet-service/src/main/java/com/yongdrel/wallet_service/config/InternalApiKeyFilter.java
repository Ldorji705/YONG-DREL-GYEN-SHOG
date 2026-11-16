package com.yongdrel.wallet_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Protects the internal write endpoint (POST /api/wallets/credit).
 * A caller that sends the correct X-Internal-Api-Key header is given
 * ROLE_INTERNAL_SERVICE. Users with a normal JWT never get this role,
 * so they cannot credit wallets (ADMIN still can, see SecurityConfig).
 *
 * Not a @Component on purpose: it is only added to the security chain,
 * so it does not also run as a general servlet filter.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    public static final String ROLE = "INTERNAL_SERVICE";
    private static final String PROTECTED_PATH = "/api/wallets/credit";

    private final byte[] expectedKey;

    public InternalApiKeyFilter(String expectedKey) {
        // Blank key = feature off: nobody can pass (fail closed).
        this.expectedKey = (expectedKey == null || expectedKey.isBlank())
                ? null
                : expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && PROTECTED_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String provided = request.getHeader(HEADER);

        if (expectedKey != null && provided != null
                // Constant-time comparison so the key can't be guessed by timing.
                && MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {

            var auth = new UsernamePasswordAuthenticationToken(
                    "internal-service",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }
}
