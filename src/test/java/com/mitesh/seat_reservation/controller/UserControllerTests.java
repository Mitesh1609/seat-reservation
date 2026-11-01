package com.mitesh.seat_reservation.controller;

import com.mitesh.seat_reservation.config.SecurityConfig;
import com.mitesh.seat_reservation.dto.CreateUserRequest;
import com.mitesh.seat_reservation.entity.User;
import com.mitesh.seat_reservation.repository.UserRepository;
import com.mitesh.seat_reservation.service.UserService;
import com.mitesh.seat_reservation.utility.Role;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({UserService.class, SecurityConfig.class})
class UserControllerTests {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository repository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createsUserWithoutAuthenticationAndIgnoresClientAssignedIdentityAndRole() throws Exception {
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertEquals("mitesh", user.getUsername());
            org.junit.jupiter.api.Assertions.assertEquals(Role.USER, user.getRole());
            org.junit.jupiter.api.Assertions.assertEquals(0L, user.getId());
            assertNotEquals("example-password-123", user.getPasswordHash());
            assertTrue(passwordEncoder.matches("example-password-123", user.getPasswordHash()));
            assertFalse(user.toString().contains(user.getPasswordHash()));
            user.setId(1L);
            return user;
        });

        mvc.perform(post("/web/v1/user/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"  mitesh  ","password":"example-password-123","id":99,"role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("mitesh"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    void rejectsMissingBlankAndOverlongUsernamesWithoutSaving() throws Exception {
        for (String body : new String[]{"{\"password\":\"example-password-123\"}",
                "{\"username\":null,\"password\":\"example-password-123\"}",
                "{\"username\":\"   \",\"password\":\"example-password-123\"}",
                "{\"username\":\"" + "a".repeat(256) + "\",\"password\":\"example-password-123\"}"}) {
            mvc.perform(post("/web/v1/user/create")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMalformedJsonWithoutSaving() throws Exception {
        mvc.perform(post("/web/v1/user/create")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void returnsConflictForDatabaseUsernameCollision() throws Exception {
        var violation = new ConstraintViolationException("Duplicate username",
                new SQLException("Duplicate", "23505"), "uk_users_username");
        when(repository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate", violation));

        mvc.perform(post("/web/v1/user/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mitesh\",\"password\":\"example-password-123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingBlankShortAndOverlongPasswordsWithoutSaving() throws Exception {
        for (String body : new String[]{"{\"username\":\"mitesh\"}",
                "{\"username\":\"mitesh\",\"password\":null}",
                "{\"username\":\"mitesh\",\"password\":\"        \"}",
                "{\"username\":\"mitesh\",\"password\":\"short\"}",
                "{\"username\":\"mitesh\",\"password\":\"" + "a".repeat(73) + "\"}",
                "{\"username\":\"mitesh\",\"password\":\"" + "é".repeat(37) + "\"}"}) {
            mvc.perform(post("/web/v1/user/create")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void saltsEachHashAndPreservesPasswordWhitespace() throws Exception {
        List<String> hashes = new ArrayList<>();
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            hashes.add(user.getPasswordHash());
            user.setId((long) hashes.size());
            return user;
        });
        for (String username : List.of("first", "second")) {
            mvc.perform(post("/web/v1/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"  exact password  \"}"))
                    .andExpect(status().isCreated());
        }
        assertNotEquals(hashes.get(0), hashes.get(1));
        for (String hash : hashes) {
            assertTrue(passwordEncoder.matches("  exact password  ", hash));
            assertFalse(passwordEncoder.matches("exact password", hash));
        }
    }

    @Test
    void requestStringDoesNotExposePassword() {
        assertFalse(new CreateUserRequest("mitesh", "example-password-123")
                .toString().contains("example-password-123"));
    }

    @Test
    void otherRoutesStillRequireAuthentication() throws Exception {
        mvc.perform(get("/shows/1")).andExpect(status().isUnauthorized());
    }
}
