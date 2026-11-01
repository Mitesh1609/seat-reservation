package com.mitesh.seat_reservation.dto;

import com.mitesh.seat_reservation.utility.Role;

public record UserResponse(Long id, String username, Role role) {
}
