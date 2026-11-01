package com.mitesh.seat_reservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// User identity must come from the authenticated principal, not this request.
public record ReserveSeatsRequest(
        List<String> seats,
        @JsonProperty("idempotency_key") String idempotencyKey
) {
}
