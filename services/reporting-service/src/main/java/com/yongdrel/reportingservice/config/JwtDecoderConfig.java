package com.yongdrel.reportingservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Validates RS256 tokens from the Auth Service: signature, expiry, issuer, audience
 * and a numeric subject (the Auth Service puts the numeric user id in "sub").
 */
@Configuration
public class JwtDecoderConfig {

    static final String NUMERIC_SUBJECT = "^[1-9]\\d{0,18}$";

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.public-key}") Resource publicKeyFile,
                                 @Value("${app.jwt.issuer}") String issuer,
                                 @Value("${app.jwt.audience}") String audience) throws Exception {
        String pem = publicKeyFile.getContentAsString(StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));

        return build(publicKey, issuer, audience);
    }

    static JwtDecoder build(RSAPublicKey publicKey, String issuer, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>("sub", sub -> sub != null && sub.matches(NUMERIC_SUBJECT))));
        return decoder;
    }
}
