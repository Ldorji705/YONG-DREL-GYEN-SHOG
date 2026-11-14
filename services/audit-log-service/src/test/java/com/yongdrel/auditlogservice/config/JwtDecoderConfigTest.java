package com.yongdrel.auditlogservice.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real RS256 tokens against the production decoder: issuer, audience, expiry, signature, numeric subject. */
class JwtDecoderConfigTest {

    private static KeyPair keys;
    private static KeyPair otherKeys;
    private static JwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
        otherKeys = gen.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        decoder = new JwtDecoderConfig().jwtDecoder(
                new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8)), "yongdrel-auth", "yongdrel-api");
    }

    private static String token(KeyPair signer, String issuer, String audience, String subject, Instant expiresAt)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expiresAt))
                .claim("role", "ORGANIZER")
                .claim("tenantId", 10);
        if (subject != null) {
            claims.subject(subject);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
        return jwt.serialize();
    }

    private static Instant inFifteenMinutes() {
        return Instant.now().plusSeconds(900);
    }

    @Test
    void validAuthServiceTokenIsAccepted() throws Exception {
        Jwt jwt = decoder.decode(token(keys, "yongdrel-auth", "yongdrel-api", "42", inFifteenMinutes()));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ORGANIZER");
        assertThat(((RSAPublicKey) keys.getPublic()).getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        String t = token(keys, "evil-issuer", "yongdrel-api", "42", inFifteenMinutes());
        assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        String t = token(keys, "yongdrel-auth", "other-api", "42", inFifteenMinutes());
        assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String t = token(keys, "yongdrel-auth", "yongdrel-api", "42", Instant.now().minusSeconds(3600));
        assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String t = token(otherKeys, "yongdrel-auth", "yongdrel-api", "42", inFifteenMinutes());
        assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String[] parts = token(keys, "yongdrel-auth", "yongdrel-api", "42", inFifteenMinutes()).split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"yongdrel-auth\",\"aud\":[\"yongdrel-api\"],\"sub\":\"1\",\"role\":\"ADMIN\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> decoder.decode(parts[0] + "." + forged + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @ParameterizedTest(name = "subject \"{0}\" is rejected")
    @ValueSource(strings = {"abc", "user@example.com", "0", "-5", "12a", "99999999999999999999"})
    void nonNumericSubjectIsRejected(String subject) throws Exception {
        String t = token(keys, "yongdrel-auth", "yongdrel-api", subject, inFifteenMinutes());
        assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void missingSubjectIsRejected() throws Exception {
        String t = token(keys, "yongdrel-auth", "yongdrel-api", null, inFifteenMinutes());
        assertThatThrownBy(() -> decoder.decode(t)).isInstanceOf(JwtException.class);
    }
}
