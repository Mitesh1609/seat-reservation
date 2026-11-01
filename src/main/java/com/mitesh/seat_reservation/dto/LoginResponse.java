package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(@JsonProperty("access_token") String accessToken,
                            @JsonProperty("token_type") String tokenType,
                            @JsonProperty("expires_in") long expiresIn) {
    @Override
    public String toString() {
        return "LoginResponse[accessToken=[REDACTED], tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
