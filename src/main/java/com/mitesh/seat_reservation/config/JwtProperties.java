package com.mitesh.seat_reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import java.time.Duration;

@ConfigurationProperties("app.jwt")
public record JwtProperties(Resource privateKey, Resource publicKey, String issuer,
                            String audience, Duration accessTokenTtl) {
    public JwtProperties {
        if (privateKey == null || publicKey == null || issuer == null || issuer.isBlank()
                || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT keys, issuer and audience must be configured");
        }
        if (accessTokenTtl == null || accessTokenTtl.compareTo(Duration.ofSeconds(1)) < 0
                || accessTokenTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("JWT lifetime must be between 1 second and 1 hour");
        }
    }
}
