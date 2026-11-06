package com.mitesh.seat_reservation.service;

import com.mitesh.seat_reservation.dto.CreateUserRequest;
import com.mitesh.seat_reservation.dto.UserResponse;
import com.mitesh.seat_reservation.entity.User;
import com.mitesh.seat_reservation.repository.UserRepository;
import com.mitesh.seat_reservation.utility.Role;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        return createUser(request, Role.USER);
    }

    @Transactional
    public UserResponse createAdmin(CreateUserRequest request) {
        return createUser(request, Role.ADMIN);
    }

    private UserResponse createUser(CreateUserRequest request, Role role) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        String username = request.username().strip();
        if (username.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be at most 255 characters");
        }
        String password = request.password();
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }

        User user = new User();
        user.setUsername(username);
        // Preserve the exact password, including whitespace, and persist only its hash.
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        try {
            // The unique constraint decides concurrent duplicate requests atomically.
            User saved = userRepository.saveAndFlush(user);
            return new UserResponse(saved.getId(), saved.getUsername(), saved.getRole());
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uk_users_username".equalsIgnoreCase(violation.getConstraintName())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists", exception);
                }
            }
            throw exception;
        }
    }
}
