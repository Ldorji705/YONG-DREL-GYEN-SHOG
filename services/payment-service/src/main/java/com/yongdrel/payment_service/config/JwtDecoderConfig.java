package com.yongdrel.payment_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}")
            Resource publicKeyResource,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience
    ) throws Exception {

        String publicPem = publicKeyResource
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(publicPem);

        RSAPublicKey publicKey = (RSAPublicKey)
                KeyFactory.getInstance("RSA").generatePublic(
                        new X509EncodedKeySpec(decoded)
                );

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(issuer);

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "Invalid JWT audience",
                            null
                    )
            );
        };

        OAuth2TokenValidator<Jwt> subjectValidator = jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_token",
                                "Invalid JWT subject",
                                null
                        )
                );
            }
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerValidator,
                        audienceValidator,
                        subjectValidator
                )
        );

        return decoder;
    }
}
