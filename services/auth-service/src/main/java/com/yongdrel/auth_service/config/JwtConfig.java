
package com.yongdrel.authservice.config;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${app.jwt.private-key}") Resource privateKeyFile,
            @Value("${app.jwt.public-key}") Resource publicKeyFile
    ) throws Exception {

        String privatePem = privateKeyFile
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        String publicPem = publicKeyFile
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        KeyFactory factory = KeyFactory.getInstance("RSA");

        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(privatePem)
                )
        );

        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(
                        Base64.getDecoder().decode(publicPem)
                )
        );

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();

        return new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(
                        new com.nimbusds.jose.jwk.JWKSet(rsaKey)
                )
        );
    }
}

