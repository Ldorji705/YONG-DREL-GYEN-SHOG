
package com.yongdrel.authservice.auth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
