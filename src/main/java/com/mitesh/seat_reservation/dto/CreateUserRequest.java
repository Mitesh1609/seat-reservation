package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Public user creation must not let callers assign themselves an admin role.
public record CreateUserRequest(
        String username,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password
) {
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", password=[REDACTED]]";
    }
}
