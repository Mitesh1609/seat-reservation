package com.mitesh.seat_reservation.controller;

import com.mitesh.seat_reservation.dto.LoginRequest;
import com.mitesh.seat_reservation.dto.LoginResponse;
import com.mitesh.seat_reservation.service.AuthService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/web/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(authService.login(request));
    }
}
