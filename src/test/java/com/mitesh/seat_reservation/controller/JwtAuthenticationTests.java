package com.mitesh.seat_reservation.controller;

import com.mitesh.seat_reservation.config.*;
import com.mitesh.seat_reservation.entity.User;
import com.mitesh.seat_reservation.repository.UserRepository;
import com.mitesh.seat_reservation.service.AuthService;
import com.mitesh.seat_reservation.utility.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, AuthService.class, JwtAuthenticationTests.TestEndpoints.class})
class JwtAuthenticationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtProperties properties;
    @MockitoBean private UserRepository repository;

    private static final String PASSWORD = "example-password-123";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final Path[] KEYS = generateKeys();

    private static Path[] generateKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Path privateKey = Files.createTempFile("seat-jwt-test-private-", ".pem");
            Path publicKey = Files.createTempFile("seat-jwt-test-public-", ".pem");
            privateKey.toFile().deleteOnExit();
            publicKey.toFile().deleteOnExit();
            Files.writeString(privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            return new Path[]{privateKey, publicKey};
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getEncoder().encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.private-key", () -> KEYS[0].toUri().toString());
        registry.add("app.jwt.public-key", () -> KEYS[1].toUri().toString());
        registry.add("app.jwt.issuer", () -> "test-issuer");
        registry.add("app.jwt.audience", () -> "test-audience");
        registry.add("app.jwt.access-token-ttl", () -> "15m");
    }

    @BeforeEach
    void users() {
        User user = new User();
        user.setId(42L);
        user.setUsername("mitesh");
        user.setPasswordHash(HASH);
        user.setRole(Role.USER);
        when(repository.findByUsername("mitesh")).thenReturn(Optional.of(user));
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());
    }

    @Test
    void loginIssuesSignedTokenWithDatabaseIdentityAndNoSession() throws Exception {
        var result = mvc.perform(post("/web/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":" mitesh ","password":"example-password-123","user_id":99,"roles":["ADMIN"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        // Decode the response with Boot's Jackson mapper to obtain the actual issued token.
        String body = result.getResponse().getContentAsString();
        String token = new tools.jackson.databind.json.JsonMapper().readTree(body).get("access_token").asText();
        Jwt jwt = decoder.decode(token);
        assertEquals("42", jwt.getSubject());
        assertEquals(List.of("USER"), jwt.getClaimAsStringList("roles"));
        assertEquals(properties.issuer(), jwt.getClaimAsString("iss"));
        assertEquals(List.of(properties.audience()), jwt.getAudience());
        assertEquals(900, java.time.Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
        mvc.perform(get("/test/identity?user_id=99").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user_id").value("42"));
    }

    @Test
    void badCredentialsReturnUnauthorizedWithoutToken() throws Exception {
        for (String body : List.of(
                "{\"username\":\"missing\",\"password\":\"example-password-123\"}",
                "{\"username\":\"mitesh\",\"password\":\"wrong-password\"}")) {
            mvc.perform(post("/web/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.access_token").doesNotExist());
        }
    }

    @Test
    void malformedLoginReturnsBadRequest() throws Exception {
        for (String body : List.of("{}", "{", "{\"username\":\"mitesh\"}")) {
            mvc.perform(post("/web/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void requiresBearerTokenInsteadOfBasicOrSessionAuthentication() throws Exception {
        mvc.perform(get("/test/identity")).andExpect(status().isUnauthorized());
        mvc.perform(get("/test/identity").header(HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString("mitesh:example-password-123".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleRulesAllowOnlyAdminToCreateShow() throws Exception {
        mvc.perform(post("/shows").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/shows").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsExpiredWrongIssuerWrongAudienceMissingExpiryAndInvalidSubject() throws Exception {
        Instant now = Instant.now();
        List<JwtClaimsSet> claims = List.of(
                claims("42", properties.issuer(), properties.audience(), now.minusSeconds(300)),
                claims("42", "wrong-issuer", properties.audience(), now.plusSeconds(300)),
                claims("42", properties.issuer(), "wrong-audience", now.plusSeconds(300)),
                claims("42", properties.issuer(), properties.audience(), null),
                claims("not-a-user-id", properties.issuer(), properties.audience(), now.plusSeconds(300)));
        for (JwtClaimsSet claim : claims) {
            mvc.perform(get("/test/identity").header(HttpHeaders.AUTHORIZATION, "Bearer " + encode(claim)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        String[] parts = token("USER").split("\\.");
        parts[2] = (parts[2].charAt(0) == 'a' ? "b" : "a") + parts[2].substring(1);
        mvc.perform(get("/test/identity").header(HttpHeaders.AUTHORIZATION, "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherInstanceWithSameConfigurationAcceptsToken() throws Exception {
        var secondInstance = new JwtConfig();
        var keys = secondInstance.jwtKeyPair(properties);
        JwtDecoder secondDecoder = secondInstance.jwtDecoder(keys, properties);
        assertEquals("42", secondDecoder.decode(token("USER")).getSubject());
        JwtEncoder secondEncoder = secondInstance.jwtEncoder(keys);
        String otherToken = secondEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims("42", properties.issuer(), properties.audience(), Instant.now().plusSeconds(300))))
                .getTokenValue();
        assertEquals("42", decoder.decode(otherToken).getSubject());
    }

    private String token(String role) {
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(List.of(properties.audience())).subject("42")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of(role)).build();
        return encode(claims);
    }

    private JwtClaimsSet claims(String subject, String issuer, String audience, Instant expiresAt) {
        var builder = JwtClaimsSet.builder().subject(subject).issuer(issuer)
                .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(600))
                .claim("roles", List.of("USER"));
        if (expiresAt != null) {
            builder.expiresAt(expiresAt);
        }
        return builder.build();
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    @RestController
    static class TestEndpoints {
        @GetMapping("/test/identity")
        Map<String, String> identity(@AuthenticationPrincipal Jwt jwt) {
            return Map.of("user_id", jwt.getSubject());
        }

        @PostMapping("/shows")
        @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
        void createShow() {
        }
    }
}
