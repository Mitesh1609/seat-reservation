package com.mitesh.seat_reservation.controller;

import com.mitesh.seat_reservation.dto.ReservationResponse;
import com.mitesh.seat_reservation.dto.ReserveSeatsRequest;
import com.mitesh.seat_reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/web/v1")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{id}/reserve")
    public ResponseEntity<ReservationResponse> reserveSeats(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReserveSeatsRequest request){
        Long userId = Long.valueOf(jwt.getSubject());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.reserveSeats(id, userId, request));
    }
}
