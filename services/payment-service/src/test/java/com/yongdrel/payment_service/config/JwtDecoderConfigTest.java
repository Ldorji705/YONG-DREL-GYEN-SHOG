package com.yongdrel.payment_service.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtDecoderConfigTest {

    private static final String ISSUER = "yongdrel-auth";
    private static final String AUDIENCE = "yongdrel-api";
    private static JwtEncoder encoder;
    private static JwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        encoder = NimbusJwtEncoder.withKeyPair(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate()
        ).build();

        String publicKey = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded()
        );
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + publicKey
                + "\n-----END PUBLIC KEY-----";

        decoder = new JwtDecoderConfig().jwtDecoder(
                new ByteArrayResource(pem.getBytes()),
                ISSUER,
                AUDIENCE
        );
    }

    @Test
    void acceptsValidUuidSubjectToken() {
        assertDoesNotThrow(() ->
                decoder.decode(token(UUID.randomUUID().toString(),
                        ISSUER, AUDIENCE, Instant.now().plusSeconds(300)))
        );
    }

    @Test
    void rejectsInvalidIssuer() {
        assertThrows(
                RuntimeException.class,
                () -> decoder.decode(token(
                        UUID.randomUUID().toString(),
                        "unexpected-issuer",
                        AUDIENCE,
                        Instant.now().plusSeconds(300)
                ))
        );
    }

    @Test
    void rejectsInvalidAudience() {
        assertThrows(
                RuntimeException.class,
                () -> decoder.decode(token(
                        UUID.randomUUID().toString(),
                        ISSUER,
                        "unexpected-audience",
                        Instant.now().plusSeconds(300)
                ))
        );
    }

    @Test
    void rejectsExpiredToken() {
        assertThrows(
                RuntimeException.class,
                () -> decoder.decode(token(
                        UUID.randomUUID().toString(),
                        ISSUER,
                        AUDIENCE,
                        Instant.now().minusSeconds(1)
                ))
        );
    }

    @Test
    void rejectsMalformedUuidSubject() {
        assertThrows(
                RuntimeException.class,
                () -> decoder.decode(token(
                        "legacy-user-id",
                        ISSUER,
                        AUDIENCE,
                        Instant.now().plusSeconds(300)
                ))
        );
    }

    private static String token(
            String subject,
            String issuer,
            String audience,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .claim("role", "USER")
                .build();

        return encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256).build(),
                        claims
                )
        ).getTokenValue();
    }
}
