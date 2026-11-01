package com.mitesh.seat_reservation.service;

import com.mitesh.seat_reservation.config.JwtProperties;
import com.mitesh.seat_reservation.dto.LoginRequest;
import com.mitesh.seat_reservation.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public AuthService(AuthenticationManager authenticationManager, JwtEncoder encoder, JwtProperties properties) {
        this.authenticationManager = authenticationManager;
        this.encoder = encoder;
        this.properties = properties;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.username().strip().length() > 255 || request.password() == null
                || request.password().isBlank()
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid username and password are required");
        }
        org.springframework.security.core.Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username().strip(), request.password()));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Instant now = Instant.now();
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring(5)).toList();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim("roles", roles)
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", properties.accessTokenTtl().toSeconds());
    }
}
