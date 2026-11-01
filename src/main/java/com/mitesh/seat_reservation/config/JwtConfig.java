package com.mitesh.seat_reservation.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    @Bean
    public KeyPair jwtKeyPair(JwtProperties properties) throws IOException, GeneralSecurityException {
        String privatePem;
        String publicPem;
        try (var input = properties.privateKey().getInputStream()) {
            privatePem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
        try (var input = properties.publicKey().getInputStream()) {
            publicPem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
        var factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(
                decodePem(privatePem, "PRIVATE KEY")));
        var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(
                decodePem(publicPem, "PUBLIC KEY")));
        if (publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalArgumentException("JWT requires a matching RSA key pair of at least 2048 bits");
        }
        return new KeyPair(publicKey, privateKey);
    }

    private byte[] decodePem(String pem, String type) {
        if (!pem.contains("-----BEGIN " + type + "-----")) {
            throw new IllegalArgumentException("Expected PEM " + type);
        }
        return Base64.getDecoder().decode(pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "").replaceAll("\\s", ""));
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair jwtKeyPair) {
        var key = new RSAKey.Builder((RSAPublicKey) jwtKeyPair.getPublic())
                .privateKey(jwtKeyPair.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair jwtKeyPair, JwtProperties properties) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtKeyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<List<String>>("aud", audiences ->
                        audiences != null && audiences.contains(properties.audience())),
                new JwtClaimValidator<Instant>("exp", Objects::nonNull),
                new JwtClaimValidator<String>("sub", this::validUserId)));
        return decoder;
    }

    private boolean validUserId(String subject) {
        try {
            return subject != null && Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
